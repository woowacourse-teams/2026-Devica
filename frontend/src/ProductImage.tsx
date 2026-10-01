import { useState } from 'react';
import './ProductImage.css';

type Props = {
  imageUrl: string | null;
  name: string;
  className: string;
  loading?: 'eager' | 'lazy';
};

export function ProductImage({ imageUrl, name, className, loading = 'lazy' }: Props) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = imageUrl !== null && imageUrl !== '' && imageUrl !== failedUrl;

  return (
    <div className={className}>
      {showImage ? (
        <img
          className="product-image__photo"
          src={imageUrl}
          alt={`${name} 제품 이미지`}
          loading={loading}
          onError={() => setFailedUrl(imageUrl)}
        />
      ) : (
        <span className="product-image__fallback" role="img" aria-label={`${name} 이미지 없음`}>
          이미지를 표시할 수 없습니다
        </span>
      )}
    </div>
  );
}
