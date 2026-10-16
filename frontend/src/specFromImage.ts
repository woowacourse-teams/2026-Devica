// 시스템 정보 캡처를 OCR 로 읽어 현재 사양 질문의 답 코드로 바꾼다.
import type { Answers } from './questions';

// 한국어 모델은 한글 키워드는 잘 읽지만 "16GB"·"1TB" 를 "1668"·"118" 로 읽는다. 영어 모델은 그 반대다.
// 두 번 읽어 같은 줄끼리 이어 붙인다. 줄 나누기는 두 모델이 같다.
export async function readSpecImage(file: File): Promise<Answers> {
  const { recognize } = await import('tesseract.js');
  const image = await upscale(file);
  const [korean, english] = await Promise.all([recognize(image, 'kor+eng'), recognize(image, 'eng')]);
  return parseSpecText(mergeLines(korean.data.text, english.data.text));
}

// 배율 100% 캡처는 글자가 작아 "i7" 을 "17" 로, "2.98 GB" 를 "298 GB" 로 읽는다. 2배로는 소수점이 빠지는 경우가 있어 3배로 키운다.
async function upscale(file: File): Promise<HTMLCanvasElement> {
  const bitmap = await createImageBitmap(file);
  const scale = bitmap.width < 2400 ? 3 : 1;
  const canvas = document.createElement('canvas');
  canvas.width = bitmap.width * scale;
  canvas.height = bitmap.height * scale;
  const context = canvas.getContext('2d');
  if (context !== null) {
    // 기본 보간은 흐려서 소수점이 사라진다.
    context.imageSmoothingQuality = 'high';
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  }
  return canvas;
}

export function mergeLines(korean: string, english: string): string {
  const lines = (text: string) => text.split('\n').filter((line) => line.trim() !== '');
  const englishLines = lines(english);
  return lines(korean)
    .map((line, index) => `${line} ${englishLines[index] ?? ''}`)
    .join('\n');
}

// "Rename this PC" 의 ram 에 걸리지 않도록 단어로만 찾는다.
const MEMORY = /메모리|\bRAM\b|\bMemory\b/;
const STORAGE = /저장\s?공간|저장소|Storage|SSD|용량/i;

// 현재 사양 질문 코드. OS 가 먼저 와야 프로세서 질문이 보인다.
export const SPEC_CODES = ['CURRENT_OS', 'CURRENT_MAC_CPU', 'CURRENT_WINDOWS_CPU', 'CURRENT_MEMORY', 'CURRENT_STORAGE'];

// 값을 찾은 질문만 담는다. 순서가 중요하다: OS 가 먼저 들어가야 프로세서 질문이 보인다.
export function parseSpecText(raw: string): Answers {
  // 저장 공간을 찾을 때 메모리 줄을 빼야 해서 줄바꿈은 남긴다.
  const text = raw.replace(/[^\S\n]+/g, ' ');
  const answers: Answers = {};

  const macChip = text.match(/\bM\s?\d{1,2}\s*(Pro|Max|Ultra)?\b/i);
  const isMac = /mac\s?OS|Apple\s+M\s?\d/i.test(text);
  const isWindows = /Windows|Intel|AMD|Ryzen|Core\s*\(?TM\)?/i.test(text);

  if (isMac) {
    answers.CURRENT_OS = ['MACOS'];
    if (macChip !== null) {
      const tier = (macChip[1] ?? '').toLowerCase();
      answers.CURRENT_MAC_CPU = [tier === 'pro' ? 'PRO' : tier === '' ? 'BASIC' : 'MAX'];
    }
  } else if (isWindows) {
    answers.CURRENT_OS = ['WINDOWS'];
    const cpu = windowsCpu(text);
    if (cpu !== null) {
      answers.CURRENT_WINDOWS_CPU = [cpu];
    }
  }

  // 키워드와 같은 줄의 첫 값을 쓴다("16.0GB(15.7GB 사용 가능)"). Windows 11 카드 제목 줄처럼 값이 없는 줄은 건너뛴다.
  // 128GB 를 넘으면 소수점을 놓친 것이라("3.95GB" → "395GB") 비워 둔다.
  const memoryLine = text.split('\n').find((line) => MEMORY.test(line) && sizesIn(after(line, MEMORY)).length > 0);
  const memory = memoryLine === undefined ? undefined : sizesIn(after(memoryLine, MEMORY))[0];
  if (memory !== undefined && memory <= 128) {
    answers.CURRENT_MEMORY = [memoryCode(memory)];
  }
  // 전체 용량은 남은 용량보다 크므로 가장 큰 값을 쓴다("724GB 사용 가능(총 994GB)"). 줄 앞쪽이 길어 멀리 있을 수 있어 끝까지 본다.
  // Windows 11 은 저장 공간 카드 뒤에 메모리가 나오므로 메모리 줄은 뺀다.
  // 1TB 는 Windows 에서 931GB·954GB 로 보이므로 900GB 부터 1TB 로 친다.
  const storageText = after(text, STORAGE)
    .split('\n')
    .filter((line) => !MEMORY.test(line))
    .join('\n');
  const storage = Math.max(...sizesIn(storageText));
  if (storage > 0) {
    answers.CURRENT_STORAGE = [storage <= 256 ? 'GB_256_OR_LESS' : storage < 900 ? 'UNDER_1TB' : 'TB_1_OR_MORE'];
  }
  return answers;
}

// 모델명 접미사로만 등급을 가른다. 세대 차이(예: 8세대 i7 H 와 Ultra 9 H)는 구분하지 못한다.
function windowsCpu(text: string): string | null {
  const model = text.match(
    /(?:i[3579]|Ultra\s?[3579]|Ryzen\s?(?:AI\s?)?[3579])[\s-]*(?:\d{3,5})(HX|HK|HS|H|U|P|V|G\d)?\b/i,
  );
  if (model === null) {
    return null;
  }
  const suffix = (model[1] ?? '').toUpperCase();
  if (suffix === 'HX') return 'HX';
  if (suffix === 'H' || suffix === 'HK') return 'H';
  if (suffix === 'U' || suffix.startsWith('G')) return 'U';
  return 'P_HS';
}

// 키워드가 없으면 빈 문자열이다.
function after(text: string, keyword: RegExp): string {
  const at = text.search(keyword);
  return at < 0 ? '' : text.slice(at);
}

function sizesIn(text: string): number[] {
  return [...text.matchAll(/(\d+(?:[.,]\d+)?)\s?(TB|GB)/gi)].map(
    ([, value, unit]) => Number.parseFloat(value.replace(',', '.')) * (unit.toUpperCase() === 'TB' ? 1000 : 1),
  );
}

// 사이 값(12·18·36GB)은 아래 선택지로 내린다.
function memoryCode(gb: number): string {
  if (gb >= 64) return 'GB_64_OR_MORE';
  if (gb >= 48) return 'GB_48';
  if (gb >= 32) return 'GB_32';
  if (gb >= 24) return 'GB_24';
  if (gb >= 16) return 'GB_16';
  return 'GB_8_OR_LESS';
}
