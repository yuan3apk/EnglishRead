"""ECDICT → assets/dict.db 裁剪脚本。

按词频裁到 ~8 万词，并从 exchange 字段生成 form 反查表（ran → run）。

exchange 字段格式（ECDICT 官方约定）：
    p:过去式  d:过去分词  i:现在分词  3:第三人称单数
    r:比较级  t:最高级   s:复数      0:原形   1:原形的变换形式

用法：
    python tools/build_dict.py <stardict.db>  app/src/main/assets/dict.db
源数据库从 https://github.com/skywind3000/ECDICT/releases 的 ecdict-sqlite-28.zip 解出。
"""

import os
import sqlite3
import sys

# 目标词数：够覆盖常见阅读，体积可控（HANDOFF R2 的 6–10 万区间取中）
TARGET_WORDS = 80000

# 只要这几种变换能反查回原形；0/1 是双向标记，跳过
FORM_TAGS = ("p", "d", "i", "3", "r", "t", "s")


def rank_clause():
    """按「这个词该不该进词典」排序。

    ECDICT 的 frq/bnc 只覆盖约 5.7 万词，剩下的靠 tag（cet4/toefl/gre…）、
    oxford、collins 标记是否常用。well-known 这类词的 frq/bnc 都是 0，
    只有 tag='cet4 cet6'，所以这三列必须参与排序，否则裁到边界就随机丢。
    """
    return """
        CASE WHEN frq IS NOT NULL AND frq > 0 THEN frq
             WHEN bnc IS NOT NULL AND bnc > 0 THEN bnc
             ELSE 1000000 END,
        CASE WHEN tag IS NOT NULL AND tag <> '' THEN 0 ELSE 1 END,
        CASE WHEN oxford = 1 THEN 0 ELSE 1 END,
        CASE WHEN collins > 0 THEN 0 ELSE 1 END,
        word
    """


def build(src_path, out_path):
    if not os.path.exists(src_path):
        sys.exit("源库不存在：%s" % src_path)

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    src = sqlite3.connect(src_path)
    out = sqlite3.connect(out_path)
    out.executescript("""
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        CREATE TABLE dict (
          word TEXT PRIMARY KEY,
          lemma TEXT,
          phonetic TEXT,
          translation TEXT,
          freq INTEGER,
          tag TEXT
        );
        CREATE TABLE form (
          form TEXT PRIMARY KEY,
          lemma TEXT
        );
        CREATE INDEX idx_form_lemma ON form(lemma);
    """)

    # 只保留有中文释义的词条：没有 translation 的查了也是白查
    sql = """
        SELECT word, phonetic, translation, tag, frq, bnc, exchange
        FROM stardict
        WHERE translation IS NOT NULL AND translation <> ''
          AND word NOT GLOB '*[^A-Za-z''-]*'
        ORDER BY %s ASC
        LIMIT ?
    """ % rank_clause()
    rows = src.execute(sql, (TARGET_WORDS,)).fetchall()
    print("选出词条：%d" % len(rows))

    dict_rows = []
    form_rows = []
    for word, phonetic, translation, tag, frq, bnc, exchange in rows:
        freq = frq if frq and frq > 0 else (bnc if bnc and bnc > 0 else None)
        dict_rows.append((word.lower(), word.lower(), phonetic,
                          translation.strip(), freq, tag))
        for form in forms_of(exchange):
            if form and form != word.lower():
                form_rows.append((form, word.lower()))

    out.executemany("INSERT OR IGNORE INTO dict VALUES (?,?,?,?,?,?)", dict_rows)
    out.executemany("INSERT OR IGNORE INTO form VALUES (?,?)", form_rows)
    out.commit()

    n_dict = out.execute("SELECT COUNT(*) FROM dict").fetchone()[0]
    n_form = out.execute("SELECT COUNT(*) FROM form").fetchone()[0]
    out.execute("VACUUM")
    out.close()
    src.close()

    size = os.path.getsize(out_path) / (1024 * 1024)
    print("dict=%d 条, form=%d 条, %.1f MB -> %s" % (n_dict, n_form, size, out_path))

    # 交付前自检：几个必查词必须在
    check(out_path)


def forms_of(exchange):
    """'p:ran/d:run/i:running/3:runs' → [ran, running, runs]（run 是原形，跳掉重复）"""
    if not exchange:
        return []
    got = []
    for part in exchange.split("/"):
        if ":" not in part:
            continue
        tag, _, value = part.partition(":")
        if tag not in FORM_TAGS or not value:
            continue
        for item in value.split(","):
            item = item.strip().lower()
            if item:
                got.append(item)
    return got


def check(out_path):
    c = sqlite3.connect(out_path)
    probes = ["ran", "better", "times", "well-known", "run"]
    for p in probes:
        lemma = c.execute("SELECT lemma FROM form WHERE form=?", (p,)).fetchone()
        row = c.execute("SELECT word, translation FROM dict WHERE word=?",
                        (lemma[0] if lemma else p,)).fetchone()
        status = "OK" if row and row[1] else "MISS"
        print("  %-12s %-12s -> %s" % (p, lemma[0] if lemma else p, status))
    c.close()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
