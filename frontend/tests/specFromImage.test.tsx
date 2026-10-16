import { expect, test } from 'vitest';
import { parseSpecText } from '../src/specFromImage';

test('Mac 이 Mac에 관하여', () => {
  expect(
    parseSpecText('MacBook Pro 14형 칩 Apple M2 Pro 메모리 16GB macOS Sequoia 15.1 저장 공간 494.38GB 사용 가능 / 1TB'),
  ).toEqual({
    CURRENT_OS: ['MACOS'],
    CURRENT_MAC_CPU: ['PRO'],
    CURRENT_MEMORY: ['GB_16'],
    CURRENT_STORAGE: ['TB_1_OR_MORE'],
  });
});

test('Windows 설정 > 시스템 > 정보', () => {
  expect(
    parseSpecText(
      '디바이스 사양 프로세서 11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz\n설치된 RAM 16.0GB(15.7GB 사용 가능) 저장소 512GB SSD Windows 11 Home',
    ),
  ).toEqual({
    CURRENT_OS: ['WINDOWS'],
    CURRENT_WINDOWS_CPU: ['U'],
    CURRENT_MEMORY: ['GB_16'],
    CURRENT_STORAGE: ['UNDER_1TB'],
  });
});

test('Ryzen HS, 사이 값 메모리', () => {
  expect(parseSpecText('Processor AMD Ryzen 7 7840HS Installed RAM 36 GB Windows')).toEqual({
    CURRENT_OS: ['WINDOWS'],
    CURRENT_WINDOWS_CPU: ['P_HS'],
    CURRENT_MEMORY: ['GB_32'],
  });
});

// 실제 설정 > 일반 > 정보 캡처의 OCR 결과. 전체 용량이 저장 공간 키워드에서 100자 넘게 떨어져 있다.
test('Mac 설정 정보, 전체 용량이 멀리 있음', () => {
  expect(
    parseSpecText(
      '칩 Apple M5 a Apple M5\n메모리 1668 L=E] 16GB\n저장 공간 XE Zt\n™]   Macintosh HD                        724.16GB 사용 가능(총 994.6168) ™] Macintosh HD 724.16GB AFZ 7H5(% 994.61GB)\n저장 공간 설정... NE 2Z7ENAE',
    ).CURRENT_STORAGE,
  ).toEqual(['TB_1_OR_MORE']);
});

// 실제 Windows 11 설정 > 시스템 > 정보(카드형) 캡처의 OCR 결과. 저장 공간 카드 뒤에 소수점을 놓친 메모리가 나온다.
test('Windows 11 카드형, 메모리 오독은 비우고 저장 공간에 섞지 않음', () => {
  expect(
    parseSpecText(
      'DESKTOP-3UTNROU Reramerthis PC.\n¥ Storage & Graphics Card = installed RAM & Processor\nQ 90 GB 4 MB 3.95GB\n46 GB of 90 GB used VMware SVGA 3D DRAM\nProcessor AMD Ryzen 7 5800H with Radeon Graphics 3.19 GHz\nInstalled RAM 395GB\nEdition Windows 11 Pro',
    ),
  ).toEqual({
    CURRENT_OS: ['WINDOWS'],
    CURRENT_WINDOWS_CPU: ['H'],
    CURRENT_STORAGE: ['GB_256_OR_LESS'],
  });
});

// 실제 한국어 Windows 11 설정 > 시스템 > 정보(카드형) 캡처의 OCR 결과. 카드 제목 줄의 "설치된 RAM" 에는 값이 없다.
test('한국어 Windows 11 카드형', () => {
  expect(
    parseSpecText(
      '[미 프로세서 빼 설치된 RAM 2 그래픽 카드 된 저장소 @ ZEMAM HX E RAM 2 d= FE Bl HEL\nND 홈 13th Gen Intel(R) 16.0GB 128 MB 138 TB\nWR 1.38 TB = 235 GBO|(7H 사용\n접근성 설치된 RAM 16.0GB(15.7GB 사용 가능) 4 = AXE RAM 16.0GB(15.7GB AIE ts)\n개인 정보 및 보안 저장소 1.38 TB 중 235 68이(가) 사용됨',
    ),
  ).toMatchObject({ CURRENT_OS: ['WINDOWS'], CURRENT_MEMORY: ['GB_16'], CURRENT_STORAGE: ['TB_1_OR_MORE'] });
});
