import { useEffect, useState } from 'react';
import { ProductImage } from './ProductImage';
import { fetchProduct, formatPrice, type Offer, offerCondition, type ProductDetail } from './products';
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
  // 목록에서 들어왔으면 그 목록으로, 앱 밖에서 바로 들어왔으면 전체 목록으로 돌아간다.
  backLabel: string;
  onBack: () => void;
};

export function ProductDetailView({ productId, backLabel, onBack }: Props) {
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
      {backLabel}
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
  const offers = [...product.offers].sort(
    (one, other) =>
      one.price - other.price || (one.id ?? Number.MAX_SAFE_INTEGER) - (other.id ?? Number.MAX_SAFE_INTEGER),
  );
  const lowest: Offer | undefined = offers[0];

  return (
    <section className="view-panel product-detail-view" id="product-detail-view" aria-labelledby="product-detail-title">
      {back}
      <article className="product-detail">
        <ProductImage
          imageUrl={product.imageUrl}
          name={product.name}
          className="product-detail__image-frame"
          loading="eager"
        />

        <section className="product-detail__summary">
          <p className="eyebrow">
            {product.brand} · {product.code}
          </p>
          <h2 id="product-detail-title">{product.name}</h2>
          {/* 설명이 없는 제품이 있다. 빈 문단을 두면 왼쪽 세로선만 남는다. */}
          {description !== '' && <p className="product-detail__description">{description}</p>}
          <strong className="product-detail__price">{formatPrice(lowest?.price ?? null)}</strong>
          {lowest !== undefined && (
            <p className="product-detail__source">
              {lowest.name} · {offerCondition(lowest)}
            </p>
          )}
        </section>

        <section className="product-detail__offers">
          <h3>판매처</h3>
          {offers.length === 0 ? (
            <p className="product-detail__no-offer">판매 중인 곳이 없습니다.</p>
          ) : (
            <ul className="product-offers">
              {offers.map((offer) => (
                <OfferRow
                  offer={offer}
                  lowest={offer === lowest}
                  key={offer.id ?? `${offer.name}:${offer.purchaseUrl}:${offer.price}`}
                />
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

function OfferRow({ offer, lowest }: { offer: Offer; lowest: boolean }) {
  const url = webUrl(offer.purchaseUrl);
  const action = offer.linkType === 'SEARCH' ? '검색' : offer.linkType === 'PURCHASE' ? '구매하기' : '판매처 보기';
  const observed = offer.priceBasis === 'OBSERVED_LOWEST';
  const basis = observed
    ? '확인 매물 중 최저가'
    : offer.priceBasis === 'SELLER_PRICE'
      ? '판매처 제시 가격'
      : '가격 근거 미기록';
  const date = offer.checkedAt && /^\d{4}-\d{2}-\d{2}$/.test(offer.checkedAt) ? offer.checkedAt : null;
  return (
    <li className="product-offer">
      <strong className="product-offer__name">{offer.name}</strong>
      <div className="product-offer__details">
        <p className="product-offer__context">{offerCondition(offer)}</p>
        <p className="product-offer__context">{basis}</p>
        <p className="product-offer__context">
          {date ? (
            <>
              확인일 <time dateTime={date}>{date}</time>
            </>
          ) : (
            '확인일 미기록'
          )}
        </p>
        {observed && (
          <p className="product-offer__context">
            관측 시점의 매물 기준이며 검색 결과에 같은 가격의 매물이 없을 수 있습니다.
          </p>
        )}
      </div>
      <strong className="product-offer__price">{formatPrice(offer.price)}</strong>
      {url ? (
        <a
          className={`button ${lowest ? 'button--primary' : 'button--neutral'} product-offer__buy`}
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`${offer.linkType === 'SEARCH' ? `${offer.name}에서 검색` : `${offer.name} ${action}`} (새 창)`}
        >
          {offer.linkType === 'SEARCH' ? `${offer.name}에서 검색` : action}
        </a>
      ) : (
        <span className="product-offer__buy">링크 확인 필요</span>
      )}
    </li>
  );
}

function webUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
  } catch {
    return null;
  }
}
