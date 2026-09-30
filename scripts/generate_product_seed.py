#!/usr/bin/env python3
"""검토한 제품 JSON을 MySQL/Flyway 초기 데이터 SQL로 변환한다.

실행 예:
  python3 generate_product_seed.py \
    --data-dir /path/to/catalog \
    --output /path/to/V1.6__seed_prototype_products.sql

데이터베이스와 AWS에는 접속하지 않는다. 생성된 SQL은 V1.4(checked_at),
V1.5(image_key)가 적용된 스키마에서 실행하도록 작성된다.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import parse_qs, urlparse


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def sql_text(value: str | None) -> str:
    if value is None:
        return "NULL"
    require(isinstance(value, str), f"문자열이 필요합니다: {value!r}")
    require(not any(char in value for char in ("\\", "\x00", "\r")),
            f"SQL 문자열에 지원하지 않는 문자가 있습니다: {value!r}")
    return "'" + value.replace("'", "''") + "'"


def sql_date(value: str | None) -> str:
    if value is None:
        return "NULL"
    require(isinstance(value, str), "날짜는 YYYY-MM-DD 문자열이어야 합니다")
    date.fromisoformat(value)
    return sql_text(value)


def sql_positive_int(value: int, label: str) -> str:
    require(type(value) is int and value > 0, f"{label}: 양의 정수가 필요합니다")
    return str(value)


def sql_screen(value: str) -> str:
    require(isinstance(value, str), "screenSizeInch는 소수 한 자리 문자열이어야 합니다")
    try:
        decimal = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError(f"잘못된 화면 크기: {value!r}") from exc
    require(Decimal("0") < decimal <= Decimal("99.9")
            and decimal == decimal.quantize(Decimal("0.1")),
            f"DECIMAL(3,1)에 맞지 않는 화면 크기: {value!r}")
    return format(decimal, ".1f")


def cpu_lookup(cpu: dict) -> str:
    return ("(SELECT id FROM cpu WHERE manufacturer = " + sql_text(cpu["manufacturer"])
            + " AND name = " + sql_text(cpu["name"])
            + " AND core_count = " + sql_positive_int(cpu["coreCount"], "coreCount") + ")")


def product_lookup(code: str) -> str:
    return "(SELECT id FROM product WHERE code = " + sql_text(code) + ")"


def insert(table: str, columns: list[str], values: list[str]) -> str:
    require(len(columns) == len(values), f"{table}: 열과 값의 개수가 다릅니다")
    return (f"INSERT INTO {table} (" + ", ".join(columns) + ")\nVALUES (\n    "
            + ",\n    ".join(values) + "\n);\n")


def load_and_validate(data_dir: Path) -> tuple[list[dict], list[dict]]:
    products_doc = json.loads((data_dir / "products.json").read_text(encoding="utf-8"))
    cpus_doc = json.loads((data_dir / "cpus.json").read_text(encoding="utf-8"))
    source_doc = json.loads((data_dir / "source-products.json").read_text(encoding="utf-8"))

    require(products_doc["schemaVersion"] == cpus_doc["schemaVersion"] == 1,
            "지원하지 않는 JSON 스키마 버전입니다")
    products = products_doc["products"]
    cpus = cpus_doc["cpus"]
    source = {product["id"]: product for product in source_doc["products"]}
    require(len(products) == products_doc["summary"]["productCount"] == 20,
            "제품이 20개가 아닙니다")
    require(len(cpus) == 10, "CPU가 10개가 아닙니다")
    require(len(source) == 20, "원본 제품이 20개가 아닙니다")
    require(len({cpu["cpuKey"] for cpu in cpus}) == len(cpus), "중복된 cpuKey가 있습니다")
    require(len({product["product"]["code"] for product in products}) == len(products),
            "중복된 제품 코드가 있습니다")
    require(len({product["prototypeId"] for product in products}) == len(products),
            "중복된 원본 제품 ID가 있습니다")
    cpu_by_key = {cpu["cpuKey"]: cpu for cpu in cpus}

    for cpu in cpus:
        require(len(cpu["manufacturer"]) <= 32 and len(cpu["name"]) <= 128,
                f"CPU 문자열 길이 초과: {cpu['cpuKey']}")
        sql_positive_int(cpu["coreCount"], "coreCount")
        sql_positive_int(cpu["score"], "score")

    for item in products:
        product = item["product"]
        laptop = item["laptop"]
        metadata = item["metadata"]
        original = source.get(item["prototypeId"])
        code = product["code"]
        require(original is not None, f"원본에 없는 제품: {code}")
        require(product["categoryCode"] == "LAPTOP", f"카테고리 불일치: {code}")
        require(len(product["brand"]) <= 64 and len(product["name"]) <= 128
                and len(code) <= 64, f"제품 문자열 길이 초과: {code}")
        require(laptop["cpuKey"] in cpu_by_key, f"없는 cpuKey: {code}")
        require(laptop["os"] in ("MAC", "WINDOWS"), f"OS 불일치: {code}")
        for field in ("memoryGb", "storageGb", "weightG"):
            sql_positive_int(laptop[field], field)
        sql_screen(laptop["screenSizeInch"])
        require(metadata["verificationStatus"] == "READY", f"미검수 제품: {code}")
        require(len(item["offers"]) == 1, f"판매처 개수가 예상과 다릅니다: {code}")
        offer = item["offers"][0]
        sql_positive_int(offer["price"], "price")
        require(offer["status"] in ("ON_SALE", "SOLD_OUT", "DISCONTINUED"),
                f"판매 상태 불일치: {code}")
        require(len(offer["name"]) <= 64 and len(offer["externalItemId"]) <= 64,
                f"판매처 문자열 길이 초과: {code}")
        require(original["priceKrw"] == offer["price"]
                and original["purchaseUrl"] == offer["purchaseUrl"]
                and original["checkedAt"] == metadata["priceCheckedAt"],
                f"원본 가격·구매 링크·확인일 불일치: {code}")
        vendor_id = parse_qs(urlparse(offer["purchaseUrl"]).query).get("vendorItemId", [])
        require(vendor_id == [offer["externalItemId"]], f"판매 옵션 ID 불일치: {code}")
        sql_date(product["releasedAt"])
        sql_date(metadata["priceCheckedAt"])
        image = data_dir / metadata["imageFile"]
        require(image.is_file(), f"이미지 파일 없음: {image}")
        digest = hashlib.sha256(image.read_bytes()).hexdigest()
        key = metadata["imageKey"]
        expected_key = f"devica/dev/product-images/{item['prototypeId']}/{digest}.webp"
        require(key == expected_key and len(key) <= 1024, f"이미지 키 오류: {code}")
        require(digest == metadata["imageSha256"],
                f"이미지 파일·키 해시 불일치: {code}")
    return products, cpus


def render(products: list[dict], cpus: list[dict]) -> str:
    lines = [
        "-- products.json + cpus.json + source-products.json에서 생성한 일회성 초기 데이터",
        "-- 전제: V1.2 카테고리, V1.4 checked_at, V1.5 image_key 적용",
        "-- 생성 SQL은 MySQL의 기존 DB ID를 가정하지 않는다.",
        f"-- CPU {len(cpus)}개, 제품·노트북·판매처 각 {len(products)}개",
        "",
    ]
    cpu_by_key = {cpu["cpuKey"]: cpu for cpu in cpus}
    for cpu in cpus:
        lines.append(f"-- CPU: {cpu['cpuKey']}")
        lines.append(insert("cpu",
                            ["manufacturer", "name", "core_count", "score", "created_at", "updated_at"],
                            [sql_text(cpu["manufacturer"]), sql_text(cpu["name"]),
                             sql_positive_int(cpu["coreCount"], "coreCount"),
                             sql_positive_int(cpu["score"], "score"),
                             "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP"]))

    for item in products:
        product = item["product"]
        laptop = item["laptop"]
        offer = item["offers"][0]
        metadata = item["metadata"]
        code = product["code"]
        product_id = product_lookup(code)
        lines.append(f"-- 제품: {item['prototypeId']} / {code}")
        lines.append(insert("product",
                            ["product_category_id", "brand", "name", "code", "description",
                             "released_at", "image_key", "created_at", "updated_at"],
                            ["(SELECT id FROM product_category WHERE code = 'LAPTOP')",
                             sql_text(product["brand"]), sql_text(product["name"]), sql_text(code),
                             sql_text(product["description"]), sql_date(product["releasedAt"]),
                             sql_text(metadata["imageKey"]), "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP"]))
        lines.append(insert("laptop",
                            ["id", "cpu_id", "os", "memory_gb", "storage_gb", "weight_g",
                             "screen_size_inch"],
                            [product_id, cpu_lookup(cpu_by_key[laptop["cpuKey"]]),
                             sql_text(laptop["os"]), sql_positive_int(laptop["memoryGb"], "memoryGb"),
                             sql_positive_int(laptop["storageGb"], "storageGb"),
                             sql_positive_int(laptop["weightG"], "weightG"),
                             sql_screen(laptop["screenSizeInch"])]))
        lines.append(insert("product_offer",
                            ["product_id", "name", "price", "external_item_id", "purchase_url",
                             "status", "checked_at", "created_at", "updated_at"],
                            [product_id, sql_text(offer["name"]),
                             sql_positive_int(offer["price"], "price"),
                             sql_text(offer["externalItemId"]), sql_text(offer["purchaseUrl"]),
                             sql_text(offer["status"]), sql_date(metadata["priceCheckedAt"]),
                             "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP"]))
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, type=Path, help="세 JSON과 images/가 있는 폴더")
    parser.add_argument("--output", required=True, type=Path, help="생성할 .sql 파일")
    args = parser.parse_args()
    products, cpus = load_and_validate(args.data_dir)
    sql = render(products, cpus)
    args.output.write_text(sql, encoding="utf-8")
    print(f"생성 완료: {args.output} (CPU {len(cpus)}, 제품 {len(products)})")


if __name__ == "__main__":
    main()
