import { useEffect, useState } from 'react';
import apple from './assets/sellers/apple.png';
import coupang from './assets/sellers/coupang.png';
import daangn from './assets/sellers/daangn.png';
import univstore from './assets/sellers/univstore.png';
import { ProductImage } from './ProductImage';
import { fetchProduct, formatPrice, type Offer, type ProductDetail } from './products';
import './ProductDetailView.css';

// 원본 상세는 GPU 를 뺀 넷만 보여준다. 서버는 코어 수·화면 크기·무게까지 주지만 원본에 없는 항목이다.
const DETAIL_SPECS: { code: string; label: string }[] = [
  { code: 'CPU', label: 'PROCESSOR' },
  { code: 'GPU', label: 'GPU' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
  { code: 'OS', label: 'OS' },
];

const SELLER_LOGOS: Record<string, string> = {
  쿠팡: coupang,
  당근: daangn,
  학생복지스토어: univstore,
  'Apple 공홈': apple,
};

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
    <button className="text-button view-back-button" type="button" onClick={onBack}>
      {backLabel}
    </button>
  );

  // 원본은 제품을 이미 손에 들고 있어 이 상태가 없었다. 기다리는 중과 실패를 나누고 빠져나갈 길도 남긴다.
  if (product === null) {
    return (
      <section className="view-panel flow-view product-detail-view" id="product-detail-view">
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
  // 품절 판매처도 마지막 확인 가격으로 견준다. 가격이 같으면 지금 살 수 있는 판매 중인 곳을 먼저 둔다.
  const offers = [...product.offers].sort(
    (one, other) =>
      one.price - other.price ||
      Number(isSoldOut(one)) - Number(isSoldOut(other)) ||
      (one.id ?? Number.MAX_SAFE_INTEGER) - (other.id ?? Number.MAX_SAFE_INTEGER),
  );
  const lowest: Offer | undefined = offers[0];

  return (
    <section
      className="view-panel flow-view product-detail-view"
      id="product-detail-view"
      aria-labelledby="product-detail-title"
    >
      {back}
      <article className="product-detail">
        <ProductImage
          imageUrl={product.imageUrl}
          name={product.name}
          className="product-detail__image-frame"
          loading="eager"
        />

        <section className="product-detail__summary">
          <p className="product-detail__maker">
            {product.brand} · {product.code}
          </p>
          <h1 id="product-detail-title">{product.name}</h1>
          {description !== '' && <p className="product-detail__description">{description}</p>}
          <div className="product-detail__price">
            <div className="product-detail__price-label">
              <span>최저가</span>
              {lowest !== undefined && (
                <b className="product-detail__source">
                  {lowest.name}
                  {isSoldOut(lowest) && <span className="product-detail__sold-out">일시품절</span>}
                </b>
              )}
            </div>
            <strong>{formatPrice(lowest?.price ?? null)}</strong>
          </div>
        </section>

        <section className="product-detail__offers">
          <h2>판매처</h2>
          {offers.length === 0 ? (
            <p className="product-detail__no-offer">판매 중인 곳이 없습니다.</p>
          ) : (
            <ul className="product-offers">
              {offers.map((offer) => (
                <OfferRow
                  offer={offer}
                  lowest={offer.price === lowest?.price}
                  key={offer.id ?? `${offer.name}:${offer.purchaseUrl}:${offer.price}`}
                />
              ))}
            </ul>
          )}
        </section>

        <section className="product-detail__specification">
          <h2>상세 사양</h2>
          <dl className="product-detail__specs">
            {DETAIL_SPECS.map(({ code, label }) => (
              <div className="product-detail__spec" key={code}>
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

function isSoldOut(offer: Offer): boolean {
  return offer.status === 'SOLD_OUT';
}

function OfferRow({ offer, lowest }: { offer: Offer; lowest: boolean }) {
  const url = webUrl(offer.purchaseUrl);
  const logo = SELLER_LOGOS[offer.name];
  const date = offer.checkedAt && /^\d{4}-\d{2}-\d{2}$/.test(offer.checkedAt) ? offer.checkedAt : null;
  const soldOut = isSoldOut(offer);
  // 품절 판매처가 최저가여도 지금 살 수 있는 곳처럼 구매 버튼을 강조하지 않는다.
  const primary = lowest && !soldOut;
  return (
    <li className={lowest ? 'product-offer product-offer--lowest' : 'product-offer'}>
      <div className="product-offer__name">
        {logo && <img className="product-offer__logo" src={logo} alt="" width={24} height={24} />}
        <strong>{offer.name}</strong>
        {lowest && <span className="product-offer__badge">최저가</span>}
        {soldOut && <span className="product-offer__badge product-offer__badge--sold-out">일시품절</span>}
      </div>
      <div className="product-offer__details">
        <p className="product-offer__context">
          {date ? (
            <>
              확인일 <time dateTime={date}>{date}</time>
            </>
          ) : (
            '확인일 미기록'
          )}
        </p>
        {soldOut && <p className="product-offer__context">품절 전 마지막으로 확인한 가격</p>}
        {offer.name === '당근' && <p className="product-offer__context">미개봉 매물 참고가 · 검색 결과로 이동</p>}
      </div>
      <strong className="product-offer__price">{formatPrice(offer.price)}</strong>
      {url ? (
        <a
          className={primary ? 'button button--primary product-offer__buy' : 'button product-offer__buy'}
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={soldOut ? `${offer.name} 판매처 보기 (일시품절, 새 창)` : `${offer.name} 판매처 보기 (새 창)`}
        >
          <span className="product-offer__buy-prefix">판매처 </span>보기
          <span aria-hidden="true">↗</span>
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
