package com.sielo.music.core.lyrics.lrclib

import android.os.Build
import java.lang.reflect.Method

/**
 * Phonetic/homophonic transliterator that converts non-Latin scripts
 * (Devanagari, Korean Hangul, Japanese Kana, Punjabi Gurmukhi, Cyrillic, etc.)
 * into clear English alphabet (Latin) lyrics so users worldwide can sing along.
 */
object HomophonicTransliterator {

    private val icuMethod: Method? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clazz = Class.forName("android.icu.text.Transliterator")
                clazz.getMethod("transliterate", String::class.java)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private val icuInstance: Any? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clazz = Class.forName("android.icu.text.Transliterator")
                val getInstance = clazz.getMethod("getInstance", String::class.java)
                getInstance.invoke(null, "Any-Latin; Latin-ASCII")
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun isNonLatin(text: String): Boolean {
        if (text.isBlank()) return false
        var nonLatinCount = 0
        var totalLetters = 0
        for (ch in text) {
            if (ch.isLetter()) {
                totalLetters++
                val code = ch.code
                // Check if outside basic Latin & Latin Extended
                if (code > 0x024F) {
                    nonLatinCount++
                }
            }
        }
        return totalLetters > 0 && (nonLatinCount.toFloat() / totalLetters) > 0.25f
    }

    fun transliterate(text: String): String {
        if (text.isBlank() || !isNonLatin(text)) return text

        // 1. Try Android ICU Transliterator (covers 100+ languages with high accuracy)
        try {
            val inst = icuInstance
            val meth = icuMethod
            if (inst != null && meth != null) {
                val result = meth.invoke(inst, text) as? String
                if (!result.isNullOrBlank()) {
                    return cleanTransliteratedText(result)
                }
            }
        } catch (e: Throwable) {
            // Fallback to pure-Kotlin transliteration below
        }

        // 2. Pure Kotlin phonetic transliteration fallback
        return cleanTransliteratedText(transliterateKotlin(text))
    }

    private fun cleanTransliteratedText(text: String): String {
        return text
            .replace(Regex("""\s+"""), " ")
            .replace(" ' ", "'")
            .trim()
    }

    private fun transliterateKotlin(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            val code = ch.code

            // Korean Hangul Syllables (U+AC00 - U+D7A3)
            if (code in 0xAC00..0xD7A3) {
                val syllableIndex = code - 0xAC00
                val initialIdx = syllableIndex / 588
                val vowelIdx = (syllableIndex % 588) / 28
                val finalIdx = syllableIndex % 28

                val initial = HANGUL_INITIALS.getOrElse(initialIdx) { "" }
                val vowel = HANGUL_VOWELS.getOrElse(vowelIdx) { "" }
                val finalConsonant = HANGUL_FINALS.getOrElse(finalIdx) { "" }

                sb.append(initial).append(vowel).append(finalConsonant)
                i++
                continue
            }

            // Devanagari (Hindi, Marathi, etc. U+0900 - U+097F)
            if (code in 0x0900..0x097F) {
                val devResult = transliterateDevanagariChar(input, i)
                sb.append(devResult.first)
                i += devResult.second
                continue
            }

            // Japanese Hiragana & Katakana
            if (code in 0x3040..0x30FF) {
                val kana = JAPANESE_KANA[ch]
                if (kana != null) {
                    sb.append(kana)
                    i++
                    continue
                }
            }

            // Cyrillic
            if (code in 0x0400..0x04FF) {
                val cyr = CYRILLIC_MAP[ch]
                if (cyr != null) {
                    sb.append(cyr)
                    i++
                    continue
                }
            }

            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun transliterateDevanagariChar(input: String, index: Int): Pair<String, Int> {
        val ch = input[index]
        val code = ch.code

        // Matras (vowel signs)
        val matra = DEVANAGARI_MATRAS[code]
        if (matra != null) {
            return Pair(matra, 1)
        }

        // Independent vowels
        val vowel = DEVANAGARI_VOWELS[code]
        if (vowel != null) {
            return Pair(vowel, 1)
        }

        // Consonants
        val consonant = DEVANAGARI_CONSONANTS[code]
        if (consonant != null) {
            // Check next char for virama (halant) or vowel matra
            if (index + 1 < input.length) {
                val nextCode = input[index + 1].code
                if (nextCode == 0x094D) { // Virama / Halant (suppresses inherent 'a')
                    return Pair(consonant, 2)
                }
                if (DEVANAGARI_MATRAS.containsKey(nextCode)) {
                    val matraStr = DEVANAGARI_MATRAS[nextCode] ?: ""
                    return Pair(consonant + matraStr, 2)
                }
            }
            // Inherent 'a' vowel unless followed by space or punctuation at end of word
            val hasInherentA = (index + 1 < input.length && input[index + 1].code in 0x0900..0x097F)
            return Pair(if (hasInherentA) "${consonant}a" else consonant, 1)
        }

        // Anusvara or Chandrabindu
        if (code == 0x0902 || code == 0x0901) return Pair("n", 1)
        // Visarga
        if (code == 0x0903) return Pair("h", 1)

        return Pair(ch.toString(), 1)
    }

    // Korean Hangul Tables
    private val HANGUL_INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )
    private val HANGUL_VOWELS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )
    private val HANGUL_FINALS = arrayOf(
        "", "g", "kk", "ks", "n", "nj", "nh", "d", "l", "lg",
        "lm", "lb", "ls", "lt", "lp", "lh", "m", "b", "bs", "s",
        "ss", "ng", "j", "ch", "k", "t", "p", "h"
    )

    // Devanagari Tables
    private val DEVANAGARI_VOWELS = mapOf(
        0x0905 to "a", 0x0906 to "aa", 0x0907 to "i", 0x0908 to "ee",
        0x0909 to "u", 0x090A to "oo", 0x090B to "ri", 0x090F to "e",
        0x0910 to "ai", 0x0913 to "o", 0x0914 to "au"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        0x093E to "aa", 0x093F to "i", 0x0940 to "ee", 0x0941 to "u",
        0x0942 to "oo", 0x0943 to "ri", 0x0947 to "e", 0x0948 to "ai",
        0x094B to "o", 0x094C to "au"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        0x0915 to "k", 0x0916 to "kh", 0x0917 to "g", 0x0918 to "gh", 0x0919 to "ng",
        0x091A to "ch", 0x091B to "chh", 0x091C to "j", 0x091D to "jh", 0x091E to "ny",
        0x091F to "t", 0x0920 to "th", 0x0921 to "d", 0x0922 to "dh", 0x0923 to "n",
        0x0924 to "t", 0x0925 to "th", 0x0926 to "d", 0x0927 to "dh", 0x0928 to "n",
        0x092A to "p", 0x092B to "ph", 0x092C to "b", 0x092D to "bh", 0x092E to "m",
        0x092F to "y", 0x0930 to "r", 0x0932 to "l", 0x0935 to "v", 0x0936 to "sh",
        0x0937 to "sh", 0x0938 to "s", 0x0939 to "h", 0x0958 to "q", 0x0959 to "kh",
        0x095A to "g", 0x095B to "z", 0x095C to "r", 0x095D to "rh", 0x095E to "f"
    )

    // Japanese Kana Table
    private val JAPANESE_KANA = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'ケ' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヲ' to "wo", 'ン' to "n"
    )

    // Cyrillic Table
    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'E' to "E", 'Ё' to "Yo",
        'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
        'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U",
        'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",
        'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
    )
}
