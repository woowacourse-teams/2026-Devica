-- products.json + cpus.json + source-products.json에서 생성한 일회성 초기 데이터
-- 전제: V1.2 카테고리, V1.4 checked_at, V1.5 image_key 적용
-- 생성 SQL은 MySQL의 기존 DB ID를 가정하지 않는다.
-- CPU 10개, 제품·노트북·판매처 각 20개

-- CPU: apple-m5-10-core
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Apple',
    'Apple M5',
    10,
    26763,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: apple-m5-pro-15-core
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Apple',
    'Apple M5 Pro 15-core',
    15,
    48861,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: apple-m5-pro-18-core
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Apple',
    'Apple M5 Pro 18-core',
    18,
    57153,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: intel-core-ultra-7-255h
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Intel',
    'Intel Core Ultra 7 255H',
    16,
    30785,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: intel-core-ultra-7-258v
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Intel',
    'Intel Core Ultra 7 258V',
    8,
    18867,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: intel-core-ultra-7-356h
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Intel',
    'Intel Core Ultra 7 356H',
    16,
    34251,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: intel-core-ultra-9-275hx
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Intel',
    'Intel Core Ultra 9 275HX',
    24,
    55710,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: amd-ryzen-ai-7-445
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'AMD',
    'AMD Ryzen AI 7 445',
    6,
    18257,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: amd-ryzen-9-8940hx
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'AMD',
    'AMD Ryzen 9 8940HX',
    16,
    49471,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- CPU: intel-core-ultra-7-355
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Intel',
    'Intel Core Ultra 7 355',
    8,
    20168,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-air13-m5-16-512 / MDH74KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Air 13 M5',
    'MDH74KH/A',
    'Apple M5와 16GB 메모리, 512GB SSD를 갖춘 13형 MacBook Air입니다.',
    NULL,
    'devica/dev/product-images/mac-air13-m5-16-512/7fc6561c46ad4516b1e90370b6970ccfc2d505c2d626d3e768c1c9572c7e309f.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MDH74KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    16,
    512,
    1230,
    13.6
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MDH74KH/A'),
    '쿠팡',
    2080500,
    '94919713475',
    'https://www.coupang.com/vp/products/9410641464?itemId=27961674147&vendorItemId=94919713475',
    'ON_SALE',
    '2026-08-19',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-air13-m5-24-1t / MDHD4KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Air 13 M5',
    'MDHD4KH/A',
    'Apple M5와 24GB 메모리, 1TB SSD를 갖춘 13형 MacBook Air입니다.',
    NULL,
    'devica/dev/product-images/mac-air13-m5-24-1t/15fac971cd390775e7190c9ee4603d84c01f49a7a53ada51a635a56585d7fa17.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MDHD4KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    24,
    1024,
    1230,
    13.6
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MDHD4KH/A'),
    '쿠팡',
    2840000,
    '94919713484',
    'https://www.coupang.com/vp/products/9410641464?itemId=27961674159&vendorItemId=94919713484',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-air15-m5-16-512 / MDVD4KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Air 15 M5',
    'MDVD4KH/A',
    'Apple M5와 16GB 메모리, 512GB SSD를 갖춘 15형 MacBook Air입니다.',
    NULL,
    'devica/dev/product-images/mac-air15-m5-16-512/992a366826dd55b6a3576ccdddccf3dd75ec836977fa1185a017e3d4f6b778d0.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MDVD4KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    16,
    512,
    1510,
    15.3
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MDVD4KH/A'),
    '쿠팡',
    2330640,
    '94919713458',
    'https://www.coupang.com/vp/products/9410641499?itemId=27961674328&vendorItemId=94919713458',
    'ON_SALE',
    '2026-08-19',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-air15-m5-24-1t / MDVC4KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Air 15 M5',
    'MDVC4KH/A',
    'Apple M5와 24GB 메모리, 1TB SSD를 갖춘 15형 MacBook Air입니다.',
    NULL,
    'devica/dev/product-images/mac-air15-m5-24-1t/0fe1e33447015bf032ad6f7c3bc3f76f9ffb0a0e254f22bb60b519da217f27ce.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MDVC4KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    24,
    1024,
    1510,
    15.3
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MDVC4KH/A'),
    '쿠팡',
    3126240,
    '94919713465',
    'https://www.coupang.com/vp/products/9410641499?itemId=27961674335&vendorItemId=94919713465',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5-16-1t / MDE14KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5',
    'MDE14KH/A',
    'Apple M5와 16GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5-16-1t/12448bae2f62451a865526a8a87c0c584dbb77ea3c8d91dd0875ef64adfab8ef.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MDE14KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    16,
    1024,
    1550,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MDE14KH/A'),
    '쿠팡',
    3047370,
    '93875613386',
    'https://www.coupang.com/vp/products/9140128274?itemId=26906195808&vendorItemId=93875613386',
    'ON_SALE',
    '2026-08-19',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5-24-1t / Z1KN0001B
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5',
    'Z1KN0001B',
    'Apple M5와 24GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5-24-1t/0c7bee5661de45da4b6f7feb5b2b12680008ce2f6e10f3bf951e6461b83ce4a1.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1KN0001B'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    24,
    1024,
    1550,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1KN0001B'),
    '쿠팡',
    3321990,
    '94002473679',
    'https://www.coupang.com/vp/products/9140128274?itemId=27033962329&vendorItemId=94002473679',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5-32-1t / Z1KN0001E
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5',
    'Z1KN0001E',
    'Apple M5와 32GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5-32-1t/9b59a55811cfc4ad5eeba95ef7104f5e5bd387540fec4e05460b0482fca85961.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1KN0001E'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5' AND core_count = 10),
    'MAC',
    32,
    1024,
    1550,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1KN0001E'),
    '쿠팡',
    3970000,
    '94002473487',
    'https://www.coupang.com/vp/products/9140128274?itemId=27033962079&vendorItemId=94002473487',
    'ON_SALE',
    '2026-08-19',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5pro-24-1t / Z1MH001VY
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5 Pro',
    'Z1MH001VY',
    'Apple M5 Pro와 24GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5pro-24-1t/c1fb18b4c8f274c362a6e519c3f675476e37ee80ed9ade2f52146e0e9582541d.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1MH001VY'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 15-core' AND core_count = 15),
    'MAC',
    24,
    1024,
    1600,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1MH001VY'),
    '쿠팡',
    3826900,
    '95034602880',
    'https://www.coupang.com/vp/products/9140128274?itemId=28078100250&vendorItemId=95034602880',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5pro-48-1t / Z1ML001Y3
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5 Pro 48GB',
    'Z1ML001Y3',
    'Apple M5 Pro와 48GB 메모리, 1TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5pro-48-1t/84788f7d66283e902f224f2b46e5287570e67f630cfcb4a0d310dc19621892e6.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1ML001Y3'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 15-core' AND core_count = 15),
    'MAC',
    48,
    1024,
    1600,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1ML001Y3'),
    '쿠팡',
    4846900,
    '95050035786',
    'https://www.coupang.com/vp/products/9140128274?itemId=28093741944&vendorItemId=95050035786',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro14-m5pro-48-2t / Z1MM001CH
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 14 M5 Pro 48GB 2TB',
    'Z1MM001CH',
    'Apple M5 Pro와 48GB 메모리, 2TB SSD를 갖춘 14형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro14-m5pro-48-2t/84788f7d66283e902f224f2b46e5287570e67f630cfcb4a0d310dc19621892e6.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1MM001CH'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 18-core' AND core_count = 18),
    'MAC',
    48,
    2048,
    1600,
    14.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'Z1MM001CH'),
    '쿠팡',
    5961000,
    '95050035747',
    'https://www.coupang.com/vp/products/9140128274?itemId=28093741917&vendorItemId=95050035747',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro16-m5pro-24-1t / MGE44KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 16 M5 Pro',
    'MGE44KH/A',
    'Apple M5 Pro와 24GB 메모리, 1TB SSD를 갖춘 16형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro16-m5pro-24-1t/c203dd2f680dfc16bd90ead58046097ec9b68b46e622b01e73ec04443c2e01da.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MGE44KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 18-core' AND core_count = 18),
    'MAC',
    24,
    1024,
    2140,
    16.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MGE44KH/A'),
    '쿠팡',
    4491000,
    '94919837914',
    'https://www.coupang.com/vp/products/9410700446?itemId=27961799434&vendorItemId=94919837914',
    'ON_SALE',
    '2026-08-19',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: mac-pro16-m5pro-48-1t / MGEC4KH/A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Apple',
    'MacBook Pro 16 M5 Pro',
    'MGEC4KH/A',
    'Apple M5 Pro와 48GB 메모리, 1TB SSD를 갖춘 16형 MacBook Pro입니다.',
    NULL,
    'devica/dev/product-images/mac-pro16-m5pro-48-1t/5098d91d7bcb6fb31ff622a46a44e3c1684d210075c8126b60b73d797ce542a8.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'MGEC4KH/A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 18-core' AND core_count = 18),
    'MAC',
    48,
    1024,
    2140,
    16.2
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'MGEC4KH/A'),
    '쿠팡',
    5566770,
    '94919837921',
    'https://www.coupang.com/vp/products/9410700446?itemId=27961799447&vendorItemId=94919837921',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-samsung-book6-356h / NT760VJT-A72A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Samsung',
    'Galaxy Book6',
    'NT760VJT-A72A',
    'Intel Core Ultra 7 355와 32GB 메모리, 1TB SSD를 갖춘 Galaxy Book6입니다.',
    NULL,
    'devica/dev/product-images/win-samsung-book6-356h/6849980121d6945c5f33e4ca9098bf622d67389541c2feda48dd410833197e10.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'NT760VJT-A72A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 7 355' AND core_count = 8),
    'WINDOWS',
    32,
    1024,
    1740,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'NT760VJT-A72A'),
    '쿠팡',
    2849000,
    '95107817299',
    'https://www.coupang.com/vp/products/9458954410?itemId=28152507754&vendorItemId=95107817299',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-samsung-book6-ultra-356h / NT960UJH-X72A
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Samsung',
    'Galaxy Book6 Ultra',
    'NT960UJH-X72A',
    'Intel Core Ultra 7 356H와 32GB 메모리, 1TB SSD를 갖춘 Galaxy Book6 Ultra입니다.',
    NULL,
    'devica/dev/product-images/win-samsung-book6-ultra-356h/cb1d50445980ee5a79eb054af811a54abccf5ffe578dfd2142ec7b931f6a4932.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'NT960UJH-X72A'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 7 356H' AND core_count = 16),
    'WINDOWS',
    32,
    1024,
    1890,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'NT960UJH-X72A'),
    '쿠팡',
    5829000,
    '94658532515',
    'https://www.coupang.com/vp/products/9339542914?itemId=27696774261&vendorItemId=94658532515',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-lg-grampro16-258v / 16Z90TS-GU7WK
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'LG',
    'gram Pro AI 16',
    '16Z90TS-GU7WK',
    'Intel Core Ultra 7 258V와 32GB 메모리, 1TB SSD를 갖춘 gram Pro AI 16입니다.',
    NULL,
    'devica/dev/product-images/win-lg-grampro16-258v/fe841a1d250ebdf5ba9ac224308256198eb44d17f1ec7e11624f6ef7ad1a997b.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = '16Z90TS-GU7WK'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 7 258V' AND core_count = 8),
    'WINDOWS',
    32,
    1024,
    1199,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = '16Z90TS-GU7WK'),
    '쿠팡',
    3000000,
    '95514237961',
    'https://www.coupang.com/vp/products/8670821592?itemId=25170697413&vendorItemId=95514237961',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-lg-grampro16-255h / 16Z90TR-SD7WK
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'LG',
    'gram Pro 16',
    '16Z90TR-SD7WK',
    'Intel Core Ultra 7 255H와 32GB 메모리, 1TB SSD를 갖춘 gram Pro 16입니다.',
    NULL,
    'devica/dev/product-images/win-lg-grampro16-255h/3165f5bccfd76cea263670a0c4488b94241957bc3f415d16be308f62c60de4a6.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = '16Z90TR-SD7WK'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 7 255H' AND core_count = 16),
    'WINDOWS',
    32,
    1024,
    1359,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = '16Z90TR-SD7WK'),
    '쿠팡',
    2850000,
    '92715203892',
    'https://www.coupang.com/vp/products/8829567591?itemId=25726533506&vendorItemId=92715203892',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-lenovo-ideapadpro5-356h / 83SK0009KR
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Lenovo',
    'IdeaPad Pro 5i Gen 11',
    '83SK0009KR',
    'Intel Core Ultra 7 356H와 32GB 메모리, 1TB SSD를 갖춘 IdeaPad Pro 5i입니다.',
    NULL,
    'devica/dev/product-images/win-lenovo-ideapadpro5-356h/117a9657a6042e923864074fa1d543a25b9c93e813b3405684606d1449fc7a0d.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = '83SK0009KR'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 7 356H' AND core_count = 16),
    'WINDOWS',
    32,
    1024,
    1650,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = '83SK0009KR'),
    '쿠팡',
    2449000,
    '95173538944',
    'https://www.coupang.com/vp/products/9478742459?itemId=28219563534&vendorItemId=95173538944',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-lenovo-legionpro5-275hx / 83LU000UKR
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'Lenovo',
    'Legion Pro 5i Gen 10',
    '83LU000UKR',
    'Intel Core Ultra 9 275HX와 32GB 메모리, 1TB SSD를 갖춘 Legion Pro 5i입니다.',
    NULL,
    'devica/dev/product-images/win-lenovo-legionpro5-275hx/79d451f6c70d71066693ce54402399a1e43ed2deb0689c4f418122596e6a5ecb.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = '83LU000UKR'),
    (SELECT id FROM cpu WHERE manufacturer = 'Intel' AND name = 'Intel Core Ultra 9 275HX' AND core_count = 24),
    'WINDOWS',
    32,
    1024,
    2420,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = '83LU000UKR'),
    '쿠팡',
    4649000,
    '92932462159',
    'https://www.coupang.com/vp/products/8888042529?itemId=25949552933&vendorItemId=92932462159',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-asus-zenbook14-ai7 / UM3406GA-QL151W
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'ASUS',
    'Zenbook 14',
    'UM3406GA-QL151W',
    'AMD Ryzen AI 7 445와 32GB 메모리, 1TB SSD를 갖춘 Zenbook 14입니다.',
    NULL,
    'devica/dev/product-images/win-asus-zenbook14-ai7/aa65d8c2047c1222969d956040537d67e5fa0fc1e16fbe51be862293841ca872.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'UM3406GA-QL151W'),
    (SELECT id FROM cpu WHERE manufacturer = 'AMD' AND name = 'AMD Ryzen AI 7 445' AND core_count = 6),
    'WINDOWS',
    32,
    1024,
    1280,
    14.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'UM3406GA-QL151W'),
    '쿠팡',
    2099000,
    '94616357403',
    'https://www.coupang.com/vp/products/9470833374?itemId=27653967019&vendorItemId=94616357403',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 제품: win-asus-rog-g16-8940hx / G614PM-TS171W
INSERT INTO product (product_category_id, brand, name, code, description, released_at, image_key, created_at, updated_at)
VALUES (
    (SELECT id FROM product_category WHERE code = 'LAPTOP'),
    'ASUS',
    'ROG Strix G16',
    'G614PM-TS171W',
    'AMD Ryzen 9 8940HX와 32GB 메모리, 1TB SSD를 갖춘 ROG Strix G16입니다.',
    NULL,
    'devica/dev/product-images/win-asus-rog-g16-8940hx/ab7f67e4a46f27800f312301f4fd88e66dae8179cec41250876ee28f2c639259.webp',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
VALUES (
    (SELECT id FROM product WHERE code = 'G614PM-TS171W'),
    (SELECT id FROM cpu WHERE manufacturer = 'AMD' AND name = 'AMD Ryzen 9 8940HX' AND core_count = 16),
    'WINDOWS',
    32,
    1024,
    2500,
    16.0
);

INSERT INTO product_offer (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
VALUES (
    (SELECT id FROM product WHERE code = 'G614PM-TS171W'),
    '쿠팡',
    3814940,
    '95503812693',
    'https://www.coupang.com/vp/products/9568845900?itemId=28559191661&vendorItemId=95503812693',
    'ON_SALE',
    '2026-08-14',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
