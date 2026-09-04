import { describe, it, expect } from 'vitest'
import { parseSseChunk, parseSseRemainder } from './sse'

describe('parseSseChunk', () => {
  it('reassembles structured events split across network chunks', () => {
    const first = parseSseChunk('data:{"type":"answer","data":"第一')
    expect(first.payloads).toEqual([])
    expect(parseSseChunk(first.rest + '行\\n第二行"}\r\n\r\n').payloads)
      .toEqual([{ type: 'answer', data: '第一行\n第二行' }])
    expect(parseSseRemainder('data:{"type":"done","data":{"sufficient":false}}'))
      .toEqual([{ type: 'done', data: { sufficient: false } }])
  })
  it('parses a single complete JSON-escaped data line', () => {
    const { payloads, rest } = parseSseChunk('data:"hello"\n')
    expect(payloads).toEqual(['hello'])
    expect(rest).toBe('')
  })

  it('parses multiple data lines in one chunk', () => {
    const { payloads } = parseSseChunk('data:"one"\ndata:"two"\ndata:"three"\n')
    expect(payloads).toEqual(['one', 'two', 'three'])
  })

  it('restores newline tokens escaped as JSON \\n', () => {
    const { payloads } = parseSseChunk('data:"hello\\nworld"\n')
    expect(payloads).toEqual(['hello\nworld'])
  })

  it('restores a lone newline token', () => {
    const { payloads } = parseSseChunk('data:"\\n"\n')
    expect(payloads).toEqual(['\n'])
  })

  it('keeps an incomplete trailing line in rest', () => {
    const { payloads, rest } = parseSseChunk('data:"one"\ndata:"part')
    expect(payloads).toEqual(['one'])
    expect(rest).toBe('data:"part')
  })

  it('filters [DONE] and empty payloads', () => {
    const { payloads } = parseSseChunk('data:\ndata:[DONE]\ndata:"ok"\n')
    expect(payloads).toEqual(['ok'])
  })

  it('ignores non-data lines (comments, event:, id:)', () => {
    const { payloads } = parseSseChunk(': comment\nevent: message\ndata:"real"\n')
    expect(payloads).toEqual(['real'])
  })

  it('falls back to raw payload when not valid JSON', () => {
    const { payloads } = parseSseChunk('data:plain-text\n')
    expect(payloads).toEqual(['plain-text'])
  })

  it('trims whitespace around data payload', () => {
    const { payloads } = parseSseChunk('data:  "spaced"  \n')
    expect(payloads).toEqual(['spaced'])
  })
})

describe('parseSseRemainder', () => {
  it('returns decoded payload for a final complete data line', () => {
    expect(parseSseRemainder('data:"last"')).toEqual(['last'])
  })

  it('returns newline for escaped newline remainder', () => {
    expect(parseSseRemainder('data:"\\n"')).toEqual(['\n'])
  })

  it('returns empty for non-data or [DONE] remainder', () => {
    expect(parseSseRemainder('data:[DONE]')).toEqual([])
    expect(parseSseRemainder('event: done')).toEqual([])
    expect(parseSseRemainder('')).toEqual([])
  })
})
