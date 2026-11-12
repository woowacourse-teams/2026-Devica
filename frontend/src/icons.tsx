import {
  AppWindow,
  ArrowUpDown,
  Check,
  ChevronDown,
  Cpu,
  Gpu,
  HardDrive,
  Keyboard,
  Laptop,
  ListFilter,
  type LucideIcon,
  MemoryStick,
  Menu,
  Monitor,
  Mouse,
  Palette,
  PenTool,
  Search,
  Server,
  Smartphone,
  X,
} from 'lucide-react';

// 아이콘은 모두 Lucide 에서 가져온다. 화면 읽기는 옆의 글자나 버튼 이름으로 읽으므로 아이콘은 숨긴다.
type IconProps = { size?: number; className?: string };

function icon(Icon: LucideIcon, defaultSize: number, strokeWidth = 2) {
  return function LucideWrapper({ size = defaultSize, className }: IconProps) {
    return <Icon aria-hidden="true" size={size} strokeWidth={strokeWidth} className={className} />;
  };
}

export const MenuIcon = icon(Menu, 24);
export const SearchIcon = icon(Search, 20);
export const SortIcon = icon(ArrowUpDown, 20);
export const FilterIcon = icon(ListFilter, 18);
export const ChevronIcon = icon(ChevronDown, 16, 2.2);
export const CheckIcon = icon(Check, 16, 2.6);

export function CloseIcon({ size }: { size: number }) {
  return <X aria-hidden="true" size={size} strokeWidth={2.4} />;
}

const SELECT_ICONS: Record<string, LucideIcon> = {
  LAPTOP: Laptop,
  MONITOR: Monitor,
  MOUSE: Mouse,
  KEYBOARD: Keyboard,
  BACKEND_DEVELOPMENT: Server,
  FRONTEND_DEVELOPMENT: AppWindow,
  MOBILE_DEVELOPMENT: Smartphone,
  DESIGN: PenTool,
};

/** 제품 유형과 사용 목적 카드의 아이콘. 모르는 코드면 그리지 않는다. */
export function SelectIcon({ code, size }: { code: string; size: number }) {
  const Icon = SELECT_ICONS[code];
  return Icon === undefined ? null : (
    <Icon className="select-card__icon" aria-hidden="true" size={size} strokeWidth={1.6} />
  );
}

// 권장 사양(REQUIRED_*)과 제품 사양(CPU 등)은 코드가 달라도 같은 항목이다.
const SPEC_ICONS: Record<string, LucideIcon> = {
  REQUIRED_CPU: Cpu,
  CPU: Cpu,
  REQUIRED_GPU: Gpu,
  GPU: Gpu,
  MEMORY: MemoryStick,
  STORAGE: HardDrive,
  COLOR_GAMUT: Palette,
  OS: Monitor,
};

/** 사양 항목 이름 앞의 아이콘. 항목을 빨리 구분하게 할 뿐 뜻은 이름이 전한다. */
export function SpecIcon({ code, size = 16 }: { code: string; size?: number }) {
  const Icon = SPEC_ICONS[code];
  return Icon === undefined ? null : <Icon className="spec-icon" aria-hidden="true" size={size} strokeWidth={2} />;
}

/** 사양 항목 이름. 아이콘과 이름을 한 줄에 둔다. */
export function SpecLabel({ code, label }: { code: string; label: string }) {
  return (
    <span className="spec-label">
      <SpecIcon code={code} />
      {label}
    </span>
  );
}
