#!/usr/bin/env python3
"""
Аудит исходников SilverChat на «мусорные» символы.

История проблемы: в файлы несколько раз попадали CJK-иероглифы, испанские
слова и невидимый U+00AD (мягкий перенос). В комментариях это косметика,
а вот в идентификаторах — ошибка компиляции. Поэтому скрипт разделяет
находки по двум уровням.

Разрешено: ASCII, кириллица U+0400..U+04FF, русская типографика
(тире, кавычки-«ёлочки», многоточие) и box-drawing U+2500..U+257F для
разделителей в KDoc.
"""
import sys
import unicodedata

ALLOWED_PUNCT = set('—–‐‘’“”…·›«»№')


def is_allowed(ch: str) -> bool:
    o = ord(ch)
    return (
        o < 128
        or 0x0400 <= o <= 0x04FF          # кириллица
        or 0x2500 <= o <= 0x257F           # box drawing (разделители KDoc)
        or ch in ALLOWED_PUNCT
    )


def is_identifier_char(ch: str) -> bool:
    return ch.isalnum() or ch == '_'


def scan(path: str):
    """Возвращает (критические, косметические) находки."""
    critical, cosmetic = [], []
    with open(path, encoding='utf-8') as fh:
        for lineno, line in enumerate(fh, 1):
            stripped = line.strip()
            in_comment = stripped.startswith(('//', '*', '/*'))
            for col, ch in enumerate(line, 1):
                if is_allowed(ch):
                    continue
                found = (lineno, col, ch, unicodedata.name(ch, '?'), stripped[:90])
                # В строке кода любой неразрешённый символ внутри
                # идентификатора — ошибка компиляции, а не косметика.
                if not in_comment and is_identifier_char(ch):
                    critical.append(found)
                else:
                    cosmetic.append(found)
    return critical, cosmetic


def report(paths):
    total_critical = 0
    for path in paths:
        critical, cosmetic = scan(path)
        total_critical += len(critical)
        if not critical and not cosmetic:
            continue
        print(f'\n{path}')
        for lineno, col, ch, name, ctx in critical:
            print(f'  КРИТИЧНО  L{lineno}:{col} U+{ord(ch):04X} {ch!r} {name}')
            print(f'            {ctx}')
        seen = set()
        for lineno, col, ch, name, ctx in cosmetic:
            key = (ord(ch), lineno)
            if key in seen:
                continue
            seen.add(key)
            print(f'  косметика L{lineno}:{col} U+{ord(ch):04X} {ch!r} {name}')
    print(f'\nИтого критичных находок: {total_critical}')
    return total_critical


if __name__ == '__main__':
    sys.exit(1 if report(sys.argv[1:]) else 0)
