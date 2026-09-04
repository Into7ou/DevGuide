-- 仅修复已确认的 M6.5 错误记录，保留主键和其他技术栈数据。
-- 官方归属依据：https://www.open-std.org/jtc1/sc22/wg14/（C 标准工作组）
-- 修复前本地核验：C语言 id=24，rag_ingested_at=NULL，向量库中没有 C语言 片段。
UPDATE tech_stacks
SET official_doc_url = 'https://www.open-std.org/jtc1/sc22/wg14/',
    doc_key_pages = '["https://www.open-std.org/jtc1/sc22/wg14/"]'::jsonb,
    github_search_query = 'language:C',
    category = 'backend',
    description = '通用编程语言；官方入口为 ISO/IEC JTC1/SC22/WG14 C 标准工作组。',
    aliases = concat_ws(',', NULLIF(aliases, ''), 'C,C 语言,C language,C programming language'),
    rag_ingested_at = NULL,
    updated_at = now()
WHERE name = 'C语言'
  AND official_doc_url = 'https://github.com/binary-husky/gpt_academic/wiki/online';
