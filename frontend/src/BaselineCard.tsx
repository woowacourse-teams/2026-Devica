import {Fragment} from 'react';
import type {Spec} from './recommendation';

// 원본은 PROCESSOR · MEMORY · STORAGE 만 보여주고 OS 는 카드 제목으로 쓴다.
// 권장 사양의 CPU 는 제품이 탑재한 CPU 와 코드가 다르다. 등급을 요구하는 쪽이 REQUIRED_CPU 다.
const ROWS = [
    {code: 'REQUIRED_CPU', label: 'PROCESSOR'},
    {code: 'MEMORY', label: 'MEMORY'},
    {code: 'STORAGE', label: 'STORAGE'},
];

type Props = {
    spec: Spec;
};

export function BaselineCard({spec}: Props) {
    const itemOf = (code: string) => spec.items.find((item) => item.code === code);
    const os = itemOf('OS');

    return (
        <article className="bordered-panel baseline-card">
            <p className="eyebrow">기본 권장 사양</p>
            <h2>{os?.displayValue ?? ''}</h2>
            <dl>
                {ROWS.map(({code, label}) => {
                    const item = itemOf(code);
                    if (item === undefined) {
                        return null;
                    }
                    return (
                        <Fragment key={code}>
                            <dt>{label}</dt>
                            <dd>{item.displayValue}</dd>
                        </Fragment>
                    );
                })}
            </dl>
        </article>
    );
}
