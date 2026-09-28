import { useEffect, useState } from 'react';
import { fetchProduct, formatPrice, type Offer, type ProductDetail } from './products';
import './ProductDetailView.css';

// 원본 상세는 이 넷만 보여준다. 서버는 코어 수·화면 크기·무게까지 주지만 원본에 없는 항목이다.
const DETAIL_SPECS: { code: string; label: string }[] = [
  { code: 'CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
  { code: 'OS', label: 'OS' },
];

type Props = {
  productId: number;
  onBack: () => void;
};

export function ProductDetailView({ productId, onBack }: Props) {
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let stale = false;
    setProduct(null);
    setFailed(false);
    fetchProduct(productId)
      .then((received) => !stale && setProduct(received))
      .catch(() => !stale && setFailed(true));
    // 다른 제품으로 넘어가면 이전 응답은 버린다. 늦게 오면 지금 보는 제품을 덮는다.
    return () => {
      stale = true;
    };
  }, [productId]);

  const back = (
    <button className="text-button product-detail__back" type="button" onClick={onBack}>
      ← 제품 목록으로
    </button>
  );

  // 원본은 제품을 이미 손에 들고 있어 이 상태가 없었다. 기다리는 중과 실패를 나누고 빠져나갈 길도 남긴다.
  if (product === null) {
    return (
      <section className="view-panel product-detail-view" id="product-detail-view">
        {back}
        {failed ? (
          <p className="notice" role="alert">
            제품 정보를 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
          </p>
        ) : (
          <p className="product-detail__status" aria-live="polite">
            제품 정보를 불러오는 중입니다…
          </p>
        )}
      </section>
    );
  }

  const description = product.description ?? '';
  // 싼 곳이 위로 오게 둔다. 판매처를 한눈에 견주는 것이 이 목록의 목적이다.
  const offers = [...product.offers].sort((one, other) => one.price - other.price);
  const lowest: Offer | undefined = offers[0];

  return (
    <section className="view-panel product-detail-view" id="product-detail-view" aria-labelledby="product-detail-title">
      {back}
      <article className="product-detail">
        {/* 제품 이미지 필드가 아직 응답에 없다. 틀만 두고 사진이 생기면 안을 채운다. */}
        <div className="product-detail__image-frame" />

        <section className="product-detail__summary">
          <p className="eyebrow">
            {product.brand} · {product.code}
          </p>
          <h2 id="product-detail-title">{product.name}</h2>
          {/* 설명이 없는 제품이 있다. 빈 문단을 두면 왼쪽 세로선만 남는다. */}
          {description !== '' && <p className="product-detail__description">{description}</p>}
          <strong className="product-detail__price">{formatPrice(lowest?.price ?? null)}</strong>
          {/* 원본은 여기에 확인일도 적었다. offers 에 그 값이 아직 없다. */}
          {lowest !== undefined && <p className="product-detail__source">{lowest.name} 일반 판매가</p>}
        </section>

        <section className="product-detail__offers">
          <h3>판매처</h3>
          {offers.length === 0 ? (
            <p className="product-detail__no-offer">판매 중인 곳이 없습니다.</p>
          ) : (
            <ul className="product-offers">
              {offers.map((offer) => (
                <li className="product-offer" key={offer.purchaseUrl}>
                  <span className="product-offer__name">{offer.name}</span>
                  <strong className="product-offer__price">{formatPrice(offer.price)}</strong>
                  <a
                    className="button button--primary product-offer__buy"
                    href={offer.purchaseUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    구매하기
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="product-detail__specification">
          <h3>상세 사양</h3>
          <dl className="product-detail__specs">
            {DETAIL_SPECS.map(({ code, label }) => (
              <div className="product-spec-item" key={code}>
                <dt>{label}</dt>
                <dd>{product.specs.find((item) => item.code === code)?.displayValue ?? ''}</dd>
              </div>
            ))}
          </dl>
        </section>
      </article>
    </section>
  );
}
