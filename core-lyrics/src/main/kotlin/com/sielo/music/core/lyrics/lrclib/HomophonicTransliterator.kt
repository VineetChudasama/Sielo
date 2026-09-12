package com.sielo.music.core.lyrics.lrclib

import android.os.Build
import java.lang.reflect.Method

/**
 * Phonetic/homophonic transliterator that converts non-Latin scripts
 * (Devanagari, Punjabi Gurmukhi, Korean Hangul, Japanese Kana, Cyrillic, etc.)
 * into clear, natural English phonetic lyrics so users worldwide can sing along comfortably.
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

        // 1. Primary: Custom natural phonetic transliteration (covers Punjabi, Hindi, Korean, Japanese, Cyrillic)
        val customResult = transliterateKotlin(text)
        if (customResult != text && !isNonLatin(customResult)) {
            return cleanTransliteratedText(customResult)
        }

        // 2. Fallback to Android ICU Transliterator for other world languages
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
            // No-op
        }

        return cleanTransliteratedText(customResult)
    }

    private fun cleanTransliteratedText(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val withoutDiacritics = normalized.replace(Regex("""\p{M}"""), "")
        return withoutDiacritics
            .replace("j̣", "j")
            .replace("ḳ", "k")
            .replace("ṣ", "sh")
            .replace("ẓ", "z")
            .replace("ḍ", "d")
            .replace("ṭ", "t")
            .replace("ṇ", "n")
            .replace("ṛ", "r")
            .replace("ṁ", "m")
            .replace("ḥ", "h")
            .replace(Regex("""\s+"""), " ")
            .replace(" ' ", "'")
            .trim()
    }

    private fun transliterateKotlin(input: String): String {
        val sb = StringBuilder()
        val words = input.split(Regex("""(\s+)"""))
        for (token in words) {
            if (token.isBlank()) {
                sb.append(token)
                continue
            }
            sb.append(transliterateWord(token))
        }
        return sb.toString()
    }

    private fun transliterateWord(word: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = word.length

        while (i < n) {
            val ch = word[i]
            val code = ch.code

            // Punjabi Gurmukhi (U+0A00 - U+0A7F)
            if (code in 0x0A00..0x0A7F) {
                val gurResult = transliterateGurmukhiWordChar(word, i)
                sb.append(gurResult.first)
                i += gurResult.second
                continue
            }

            // Devanagari (Hindi, Marathi, etc. U+0900 - U+097F)
            if (code in 0x0900..0x097F) {
                val devResult = transliterateDevanagariWordChar(word, i)
                sb.append(devResult.first)
                i += devResult.second
                continue
            }

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

    private fun transliterateGurmukhiWordChar(word: String, index: Int): Pair<String, Int> {
        val ch = word[index]
        val code = ch.code
        val n = word.length

        // Addak (0x0A71 ੱ) - geminates next consonant
        if (code == 0x0A71) {
            if (index + 1 < n) {
                val nextRes = transliterateGurmukhiWordChar(word, index + 1)
                val geminated = if (nextRes.first.isNotEmpty()) "${nextRes.first.first()}${nextRes.first}" else nextRes.first
                return Pair(geminated, 1 + nextRes.second)
            }
            return Pair("", 1)
        }

        // Tippi (0x0A70 ੰ) or Bindi (0x0A02 ਂ)
        if (code == 0x0A70 || code == 0x0A02) {
            return Pair("n", 1)
        }

        // Matras
        val matra = GURMUKHI_MATRAS[code]
        if (matra != null) {
            return Pair(matra, 1)
        }

        // Independent vowels
        val vowel = GURMUKHI_VOWELS[code]
        if (vowel != null) {
            if (index + 1 < n && (word[index + 1].code == 0x0A70 || word[index + 1].code == 0x0A02)) {
                return Pair("${vowel}n", 2)
            }
            return Pair(vowel, 1)
        }

        // Consonants
        val consonant = GURMUKHI_CONSONANTS[code]
        if (consonant != null) {
            if (index + 1 < n) {
                val nextCode = word[index + 1].code
                if (nextCode == 0x0A4D) { // Halant
                    return Pair(consonant, 2)
                }
                if (GURMUKHI_MATRAS.containsKey(nextCode)) {
                    val matraStr = GURMUKHI_MATRAS[nextCode] ?: ""
                    if (index + 2 < n && (word[index + 2].code == 0x0A70 || word[index + 2].code == 0x0A02)) {
                        val nas = when (matraStr) {
                            "e" -> "ein"
                            "o" -> "on"
                            "a" -> "an"
                            else -> "${matraStr}n"
                        }
                        return Pair(consonant + nas, 3)
                    }
                    return Pair(consonant + matraStr, 2)
                }
                if (nextCode == 0x0A70 || nextCode == 0x0A02) {
                    return Pair("${consonant}an", 2)
                }
            }

            // Inherent 'a' / Schwa rule
            val isWordEnd = index + 1 >= n || !word[index + 1].isLetter()
            if (isWordEnd) {
                return Pair(consonant, 1)
            }

            val isWordStart = index == 0
            val nextAfterCons = index + 2
            val nextHasMatra = nextAfterCons < n && GURMUKHI_MATRAS.containsKey(word[nextAfterCons].code)

            if (!isWordStart && nextHasMatra) {
                return Pair(consonant, 1)
            } else {
                return Pair("${consonant}a", 1)
            }
        }

        return Pair(ch.toString(), 1)
    }

    private fun transliterateDevanagariWordChar(word: String, index: Int): Pair<String, Int> {
        val ch = word[index]
        val code = ch.code
        val n = word.length

        val isNukta = (index + 1 < n && word[index + 1].code == 0x093C)

        if (DEVANAGARI_CONSONANTS.containsKey(code) || (isNukta && NUKTA_CONSONANTS.containsKey(code))) {
            val cons = if (isNukta) NUKTA_CONSONANTS[code] ?: DEVANAGARI_CONSONANTS[code] ?: "" else DEVANAGARI_CONSONANTS[code] ?: ""
            val idx = if (isNukta) index + 2 else index + 1

            if (idx < n) {
                val nextCode = word[idx].code
                if (nextCode == 0x094D) { // Virama / Halant
                    return Pair(cons, (idx - index) + 1)
                }
                if (DEVANAGARI_MATRAS.containsKey(nextCode)) {
                    val matra = DEVANAGARI_MATRAS[nextCode] ?: ""
                    if (idx + 1 < n && (word[idx + 1].code == 0x0902 || word[idx + 1].code == 0x0901)) {
                        val nas = when (matra) {
                            "e" -> "ein"
                            "o" -> "on"
                            "a" -> "an"
                            else -> "${matra}n"
                        }
                        return Pair(cons + nas, (idx - index) + 2)
                    } else {
                        return Pair(cons + matra, (idx - index) + 1)
                    }
                }
                if (nextCode == 0x0902 || nextCode == 0x0901) {
                    return Pair("${cons}an", (idx - index) + 1)
                }
            }

            // Word end
            val isWordEnd = idx >= n || !word[idx].isLetter()
            if (isWordEnd) {
                return Pair(cons, idx - index)
            }

            // Schwa deletion (e.g. mujhko, itna, karta, apna, saveron)
            val isWordStart = index == 0
            val nextIsNukta = (idx + 1 < n && word[idx + 1].code == 0x093C)
            val nextAfterCons = if (nextIsNukta) idx + 2 else idx + 1
            val nextHasMatra = (nextAfterCons < n && DEVANAGARI_MATRAS.containsKey(word[nextAfterCons].code))

            if (!isWordStart && nextHasMatra) {
                return Pair(cons, idx - index)
            } else {
                return Pair("${cons}a", idx - index)
            }
        }

        // Matras
        val matra = DEVANAGARI_MATRAS[code]
        if (matra != null) {
            return Pair(matra, 1)
        }

        // Independent vowels
        val vowel = DEVANAGARI_VOWELS[code]
        if (vowel != null) {
            if (index + 1 < n && (word[index + 1].code == 0x0902 || word[index + 1].code == 0x0901)) {
                val nas = when (vowel) {
                    "a", "aa" -> "an"
                    "u", "oo" -> "un"
                    else -> "${vowel}n"
                }
                return Pair(nas, 2)
            }
            return Pair(vowel, 1)
        }

        // Anusvara or Chandrabindu
        if (code == 0x0902 || code == 0x0901) return Pair("n", 1)
        // Visarga
        if (code == 0x0903) return Pair("h", 1)

        return Pair(ch.toString(), 1)
    }

    // Punjabi Gurmukhi Tables
    private val GURMUKHI_VOWELS = mapOf(
        0x0A05 to "a", 0x0A06 to "aa", 0x0A07 to "i", 0x0A08 to "i",
        0x0A09 to "u", 0x0A0A to "u", 0x0A0F to "e", 0x0A10 to "ai",
        0x0A13 to "o", 0x0A14 to "au"
    )

    private val GURMUKHI_MATRAS = mapOf(
        0x0A3E to "a", 0x0A3F to "i", 0x0A40 to "i", 0x0A41 to "u",
        0x0A42 to "u", 0x0A47 to "e", 0x0A48 to "ai", 0x0A4B to "o",
        0x0A4C to "au"
    )

    private val GURMUKHI_CONSONANTS = mapOf(
        0x0A15 to "k", 0x0A16 to "kh", 0x0A17 to "g", 0x0A18 to "gh", 0x0A19 to "ng",
        0x0A1A to "ch", 0x0A1B to "chh", 0x0A1C to "j", 0x0A1D to "jh", 0x0A1E to "ny",
        0x0A1F to "t", 0x0A20 to "th", 0x0A21 to "d", 0x0A22 to "dh", 0x0A23 to "n",
        0x0A24 to "t", 0x0A25 to "th", 0x0A26 to "d", 0x0A27 to "dh", 0x0A28 to "n",
        0x0A2A to "p", 0x0A2B to "f", 0x0A2C to "b", 0x0A2D to "bh", 0x0A2E to "m",
        0x0A2F to "y", 0x0A30 to "r", 0x0A32 to "l", 0x0A33 to "l", 0x0A35 to "v",
        0x0A36 to "sh", 0x0A38 to "s", 0x0A39 to "h", 0x0A59 to "kh", 0x0A5A to "g",
        0x0A5B to "z", 0x0A5C to "r", 0x0A5E to "f"
    )

    // Devanagari Tables
    private val DEVANAGARI_VOWELS = mapOf(
        0x0905 to "a", 0x0906 to "aa", 0x0907 to "i", 0x0908 to "i",
        0x0909 to "u", 0x090A to "u", 0x090B to "ri", 0x090F to "e",
        0x0910 to "ai", 0x0913 to "o", 0x0914 to "au",
        0x090D to "e", 0x0911 to "o", 0x0972 to "a"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        0x093E to "a", 0x093F to "i", 0x0940 to "i", 0x0941 to "u",
        0x0942 to "u", 0x0943 to "ri", 0x0947 to "e", 0x0948 to "ai",
        0x094B to "o", 0x094C to "au",
        0x0945 to "e", 0x0949 to "o"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        0x0915 to "k", 0x0916 to "kh", 0x0917 to "g", 0x0918 to "gh", 0x0919 to "ng",
        0x091A to "ch", 0x091B to "chh", 0x091C to "j", 0x091D to "jh", 0x091E to "ny",
        0x091F to "t", 0x0920 to "th", 0x0921 to "d", 0x0922 to "dh", 0x0923 to "n",
        0x0924 to "t", 0x0925 to "th", 0x0926 to "d", 0x0927 to "dh", 0x0928 to "n",
        0x092A to "p", 0x092B to "ph", 0x092C to "b", 0x092D to "bh", 0x092E to "m",
        0x092F to "y", 0x0930 to "r", 0x0932 to "l", 0x0933 to "l", 0x0934 to "l",
        0x0935 to "v", 0x0936 to "sh", 0x0937 to "sh", 0x0938 to "s", 0x0939 to "h",
        0x0958 to "q", 0x0959 to "kh", 0x095A to "gh", 0x095B to "z", 0x095C to "r",
        0x095D to "rh", 0x095E to "f", 0x095F to "y"
    )

    private val NUKTA_CONSONANTS = mapOf(
        0x0915 to "q",
        0x0916 to "kh",
        0x0917 to "gh",
        0x091C to "z",
        0x0921 to "r",
        0x0922 to "rh",
        0x092B to "f"
    )

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
