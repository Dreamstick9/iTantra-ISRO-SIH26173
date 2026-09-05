package org.isro.itantra.tts

import java.text.Normalizer
import java.util.regex.Pattern

data class LanguageNumberForms(
    val zero: String,
    val units: Array<String>, // 0 to 99 lookup table
    val hundred: String,
    val thousand: String,
    val lakh: String,
    val crore: String,
    val arab: String,
    val currencyUnitSingular: String,
    val currencyUnitPlural: String,
    val currencyFractionSingular: String,
    val currencyFractionPlural: String,
    val decimalPointWord: String,
    val andWord: String
)

object LanguagePacks {
    val HINDI = LanguageNumberForms(
        zero = "शून्य",
        units = arrayOf(
            "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
            "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अट्ठारह", "उन्नीस", "बीस",
            "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
            "इकत्तीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
            "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
            "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
            "इकसठ", "बासठ", "तिरेसठ", "चौंसठ", "पैंसठ", "छियासठ", "सरसठ", "अड़सठ", "उनहत्तर", "सत्तर",
            "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्नासी", "अस्सी",
            "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
            "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
        ),
        hundred = "सौ",
        thousand = "हज़ार",
        lakh = "लाख",
        crore = "करोड़",
        arab = "अरब",
        currencyUnitSingular = "रुपया",
        currencyUnitPlural = "रुपये",
        currencyFractionSingular = "पैसा",
        currencyFractionPlural = "पैसे",
        decimalPointWord = "दशमलव",
        andWord = "और"
    )

    val MARATHI = LanguageNumberForms(
        zero = "शून्य",
        units = arrayOf(
            "शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ", "दहा",
            "अकरा", "बारा", "तेरा", "चौदा", "पंधरा", "सोळा", "सतरा", "अठरा", "एकोणीस", "वीस",
            "एकवीस", "बावीस", "तेवीस", "चोवीस", "पंचवीस", "सव्वीस", "सत्तावीस", "अठ्ठावीस", "एकोणतीस", "तीस",
            "एकतीस", "बत्तीस", "तेहेतीस", "चौतीस", "पस्तीस", "छत्तीस", "सदतीस", "अडतीस", "एकोणचाळीस", "चाळीस",
            "एक्केचाळीस", "बेचाळीस", "त्रेचाळीस", "चव्वेचाळीस", "पंचेचाळीस", "शेहेचाळीस", "सत्तेचाळीस", "अठ्ठेचाळीस", "एकोणपन्नास", "पन्नास",
            "एक्कावन्न", "बावन्न", "त्रेपन्न", "चोपन्न", "पंचावन्न", "छप्पन्न", "सत्तावन्न", "अठ्ठावन्न", "एकोणसाठ", "साठ",
            "एकसष्ठ", "बासष्ठ", "त्रेसष्ठ", "चौसष्ठ", "पासष्ठ", "सहासष्ठ", "सदुसष्ठ", "अडुसष्ठ", "एकोणसत्तर", "सत्तर",
            "एकाहत्तर", "बाहत्तर", "त्र्याहत्तर", "चौर्‍याहत्तर", "पंच्याहत्तर", "शहात्तर", "सत्त्याहत्तर", "अठ्ठ्याहत्तर", "एकोणऐंशी", "ऐंशी",
            "एक्क्यांशी", "ब्यांशी", "त्र्यांशी", "चौऱ्यांशी", "पंच्यांशी", "शहांशी", "सत्त्यांशी", "अठ्ठ्यांशी", "एकोणनव्वद", "नव्वद",
            "एक्क्याण्णव", "ब्याण्णव", "त्र्याण्णव", "चौऱ्याण्णव", "पंच्याण्णव", "शहाण्णव", "सत्त्याण्णव", "अठ्ठ्याण्णव", "नव्व्याण्णव"
        ),
        hundred = "शे",
        thousand = "हजार",
        lakh = "लाख",
        crore = "कोटी",
        arab = "अब्ज",
        currencyUnitSingular = "रुपया",
        currencyUnitPlural = "रुपये",
        currencyFractionSingular = "पैसा",
        currencyFractionPlural = "पैसे",
        decimalPointWord = "पूर्णांक",
        andWord = "आणि"
    )

    val ENGLISH = LanguageNumberForms(
        zero = "zero",
        units = arrayOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
            "twenty-one", "twenty-two", "twenty-three", "twenty-four", "twenty-five", "twenty-six", "twenty-seven", "twenty-eight", "twenty-nine", "thirty",
            "thirty-one", "thirty-two", "thirty-three", "thirty-four", "thirty-five", "thirty-six", "thirty-seven", "thirty-eight", "thirty-nine", "forty",
            "forty-one", "forty-two", "forty-three", "forty-four", "forty-five", "forty-six", "forty-seven", "forty-eight", "forty-nine", "fifty",
            "fifty-one", "fifty-two", "fifty-three", "fifty-four", "fifty-five", "fifty-six", "fifty-seven", "fifty-eight", "fifty-nine", "sixty",
            "sixty-one", "sixty-two", "sixty-three", "sixty-four", "sixty-five", "sixty-six", "sixty-seven", "sixty-eight", "sixty-nine", "seventy",
            "seventy-one", "seventy-two", "seventy-three", "seventy-four", "seventy-five", "seventy-six", "seventy-seven", "seventy-eight", "seventy-nine", "eighty",
            "eighty-one", "eighty-two", "eighty-three", "eighty-four", "eighty-five", "eighty-six", "eighty-seven", "eighty-eight", "eighty-nine", "ninety",
            "ninety-one", "ninety-two", "ninety-three", "ninety-four", "ninety-five", "ninety-six", "ninety-seven", "ninety-eight", "ninety-nine"
        ),
        hundred = "hundred",
        thousand = "thousand",
        lakh = "lakh",
        crore = "crore",
        arab = "hundred crore",
        currencyUnitSingular = "rupee",
        currencyUnitPlural = "rupees",
        currencyFractionSingular = "paise",
        currencyFractionPlural = "paise",
        decimalPointWord = "point",
        andWord = "and"
    )

    fun get(lang: SupportedLanguage): LanguageNumberForms = when (lang) {
        SupportedLanguage.HINDI -> HINDI
        SupportedLanguage.MARATHI -> MARATHI
        SupportedLanguage.ENGLISH -> ENGLISH
        else -> HINDI
    }
}

/**
 * Deterministic, zero-allocation-in-loops Text Normalization Pipeline.
 * Formats non-standard words (NSWs) into clean phonetic text for offline neural TTS.
 */
object TextNormalizer {

    private val REGEX_CURRENCY_DECIMAL = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val REGEX_TIMESTAMP = Pattern.compile("\\b([0-1]?[0-9]|2[0-3]):([0-5][0-9])(?:\\s*(AM|PM|am|pm))?(?:\\s*(IST|UTC|hrs|hours))?\\b")
    private val REGEX_GEO_COORD = Pattern.compile("\\b([0-9]{1,2}(?:\\.[0-9]+)?)\\s*°?\\s*([NSEWnsew])\\b")
    private val REGEX_DECIMAL = Pattern.compile("\\b([0-9]+)\\.([0-9]+)\\b")
    private val REGEX_PERCENT = Pattern.compile("\\b([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE)
    private val REGEX_EMERGENCY_UNITS = Pattern.compile("\\b([0-9]+)\\s*(kmph|km/h|m/s|knots|hPa|mbar|m|meters)\\b", Pattern.CASE_INSENSITIVE)
    private val REGEX_CARDINAL = Pattern.compile("\\b([0-9]+(?:,[0-9]+)*)\\b")
    private val REGEX_WHITESPACE = Pattern.compile("\\s+")

    fun normalize(rawText: String, lang: SupportedLanguage = SupportedLanguage.HINDI): String {
        if (rawText.isBlank()) return ""

        var text = sanitizeAndUnifyDigits(rawText)
        text = expandEmergencyPhrases(text, lang)
        text = expandCurrency(text, lang)
        text = expandTimestampsAndCoordinates(text, lang)
        text = expandUnitsAndPercentages(text, lang)
        text = expandNumbers(text, lang)
        return finalizeProsody(text)
    }

    private fun sanitizeAndUnifyDigits(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            val converted = when (ch) {
                in '\u0966'..'\u096F' -> ('0' + (ch - '\u0966')) // Devanagari
                in '\u09E6'..'\u09EF' -> ('0' + (ch - '\u09E6')) // Bengali
                in '\u0BE6'..'\u0BEF' -> ('0' + (ch - '\u0BE6')) // Tamil
                in '\u0C66'..'\u0C6F' -> ('0' + (ch - '\u0C66')) // Telugu
                else -> ch
            }
            sb.append(converted)
        }
        return sb.toString()
    }

    private fun expandEmergencyPhrases(text: String, lang: SupportedLanguage): String {
        var result = text
        val dict = when (lang) {
            SupportedLanguage.HINDI -> mapOf(
                "ISRO" to "इसरो",
                "IMD" to "भारतीय मौसम विभाग",
                "INCOIS" to "इनकोइस",
                "NDRF" to "एन.डी.आर.एफ.",
                "PFZ" to "मत्स्य पालन क्षेत्र",
                "CAT-4" to "श्रेणी चार",
                "CAT-3" to "श्रेणी तीन",
                "CAT-5" to "श्रेणी पाँच",
                "RED ALERT" to "सावधान, लाल चेतावनी!",
                "TSUNAMI WARNING" to "सुनामी की गंभीर चेतावनी!",
                "CYCLONE ALERT" to "चक्रवात की चेतावनी!",
                "EVACUATE" to "तत्काल सुरक्षित स्थान पर जाएं",
                "DO NOT VENTURE INTO SEA" to "मछुआरों को चेतावनी, समुद्र में बिल्कुल न जाएँ!"
            )
            SupportedLanguage.MARATHI -> mapOf(
                "ISRO" to "इसरो",
                "IMD" to "हवामान विभाग",
                "INCOIS" to "इनकॉईस",
                "NDRF" to "एन.डी.आर.एफ.",
                "PFZ" to "मासेमारी क्षेत्र",
                "CAT-4" to "श्रेणी चार",
                "CAT-3" to "श्रेणी तीन",
                "RED ALERT" to "धोकादायक, लाल इशारा!",
                "TSUNAMI WARNING" to "सुनामीची गंभीर सतर्कता!",
                "CYCLONE ALERT" to "चक्रीवादळाचा इशारा!",
                "EVACUATE" to "त्वरित सुरक्षित ठिकाणी स्थलांतर करा",
                "DO NOT VENTURE INTO SEA" to "समुद्रात अजिबात जाऊ नका!"
            )
            else -> mapOf(
                "ISRO" to "I.S.R.O.",
                "IMD" to "India Meteorological Department",
                "INCOIS" to "INCOIS",
                "NDRF" to "N.D.R.F.",
                "PFZ" to "Potential Fishing Zone",
                "CAT-4" to "Category Four",
                "CAT-3" to "Category Three",
                "CAT-5" to "Category Five",
                "RED ALERT" to "Danger, Red Alert!",
                "TSUNAMI WARNING" to "Urgent, Tsunami Warning!",
                "CYCLONE ALERT" to "Cyclone Alert!",
                "EVACUATE" to "Evacuate immediately",
                "DO NOT VENTURE INTO SEA" to "Warning, do not venture into the sea!"
            )
        }

        for ((key, value) in dict) {
            result = result.replace(Regex("(?i)\\b${Pattern.quote(key)}\\b"), value)
        }
        return result
    }

    private fun expandCurrency(text: String, lang: SupportedLanguage): String {
        val forms = LanguagePacks.get(lang)
        val matcher = REGEX_CURRENCY_DECIMAL.matcher(text)
        val sb = StringBuffer()

        while (matcher.find()) {
            val amountStr = matcher.group(1)!!.replace(",", "")
            val replacement = if (amountStr.contains(".")) {
                val parts = amountStr.split(".")
                val integerPart = parts[0].toLongOrNull() ?: 0L
                var fractionalPartStr = parts[1]
                if (fractionalPartStr.length == 1) fractionalPartStr += "0"
                val fractionalPart = fractionalPartStr.take(2).toIntOrNull() ?: 0

                val intWords = numberToIndianWords(integerPart, forms)
                val intCurrencyUnit = if (integerPart == 1L) forms.currencyUnitSingular else forms.currencyUnitPlural

                if (fractionalPart > 0) {
                    val fracWords = forms.units[fractionalPart]
                    val fracUnit = if (fractionalPart == 1) forms.currencyFractionSingular else forms.currencyFractionPlural
                    "$intWords $intCurrencyUnit ${forms.andWord} $fracWords $fracUnit"
                } else {
                    "$intWords $intCurrencyUnit"
                }
            } else {
                val integerPart = amountStr.toLongOrNull() ?: 0L
                val intWords = numberToIndianWords(integerPart, forms)
                val unit = if (integerPart == 1L) forms.currencyUnitSingular else forms.currencyUnitPlural
                "$intWords $unit"
            }
            matcher.appendReplacement(sb, replacement)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun expandTimestampsAndCoordinates(text: String, lang: SupportedLanguage): String {
        val forms = LanguagePacks.get(lang)
        var result = text

        val timeMatcher = REGEX_TIMESTAMP.matcher(result)
        val sbTime = StringBuffer()
        while (timeMatcher.find()) {
            val hour = timeMatcher.group(1)!!.toInt()
            val minute = timeMatcher.group(2)!!.toInt()
            val meridiem = timeMatcher.group(3)
            val timezone = timeMatcher.group(4)

            val expandedTime = when (lang) {
                SupportedLanguage.HINDI -> {
                    val hrWords = forms.units[hour]
                    val minWords = forms.units[minute]
                    val tzWord = if (timezone != null && timezone.equals("IST", ignoreCase = true)) "भारतीय मानक समय " else ""
                    "$tzWord$hrWords बजकर $minWords मिनट"
                }
                SupportedLanguage.MARATHI -> {
                    val hrWords = forms.units[hour]
                    val minWords = forms.units[minute]
                    "$hrWords वाजून $minWords मिनिटे"
                }
                else -> {
                    val hrWords = forms.units[hour]
                    val minWords = if (minute == 0) "o'clock" else forms.units[minute]
                    val amPm = meridiem?.uppercase() ?: ""
                    val tz = timezone ?: ""
                    "$hrWords $minWords $amPm $tz".trim()
                }
            }
            timeMatcher.appendReplacement(sbTime, expandedTime)
        }
        timeMatcher.appendTail(sbTime)
        result = sbTime.toString()

        val coordMatcher = REGEX_GEO_COORD.matcher(result)
        val sbCoord = StringBuffer()
        while (coordMatcher.find()) {
            val degrees = coordMatcher.group(1)!!
            val directionChar = coordMatcher.group(2)!!.uppercase()

            val dirWord = when (lang) {
                SupportedLanguage.HINDI -> when (directionChar) {
                    "N" -> "अंश उत्तर"
                    "S" -> "अंश दक्षिण"
                    "E" -> "अंश पूर्व"
                    "W" -> "अंश पश्चिम"
                    else -> directionChar
                }
                SupportedLanguage.MARATHI -> when (directionChar) {
                    "N" -> "अंश उत्तर"
                    "S" -> "अंश दक्षिण"
                    "E" -> "अंश पूर्व"
                    "W" -> "अंश पश्चिम"
                    else -> directionChar
                }
                else -> when (directionChar) {
                    "N" -> "degrees North"
                    "S" -> "degrees South"
                    "E" -> "degrees East"
                    "W" -> "degrees West"
                    else -> directionChar
                }
            }
            coordMatcher.appendReplacement(sbCoord, "$degrees $dirWord")
        }
        coordMatcher.appendTail(sbCoord)
        return sbCoord.toString()
    }

    private fun expandUnitsAndPercentages(text: String, lang: SupportedLanguage): String {
        var result = text

        result = REGEX_PERCENT.matcher(result).replaceAll { mr ->
            val num = mr.group(1)
            when (lang) {
                SupportedLanguage.HINDI -> "$num प्रतिशत"
                SupportedLanguage.MARATHI -> "$num टक्के"
                else -> "$num percent"
            }
        }

        val unitMatcher = REGEX_EMERGENCY_UNITS.matcher(result)
        val sbUnit = StringBuffer()
        while (unitMatcher.find()) {
            val num = unitMatcher.group(1)!!
            val unit = unitMatcher.group(2)!!.lowercase()
            val unitSpoken = when (lang) {
                SupportedLanguage.HINDI -> when (unit) {
                    "kmph", "km/h" -> "किलोमीटर प्रति घंटा"
                    "m/s" -> "मीटर प्रति सेकंड"
                    "knots" -> "नॉट्स"
                    "hpa", "mbar" -> "हेक्टोपास्कल"
                    "m", "meters" -> "मीटर"
                    else -> unit
                }
                SupportedLanguage.MARATHI -> when (unit) {
                    "kmph", "km/h" -> "किलोमीटर प्रति तास"
                    "m/s" -> "मीटर प्रति सेकंद"
                    "knots" -> "नॉट्स"
                    "hpa", "mbar" -> "हेक्टोपास्कल"
                    "m", "meters" -> "मीटर"
                    else -> unit
                }
                else -> when (unit) {
                    "kmph", "km/h" -> "kilometres per hour"
                    "m/s" -> "metres per second"
                    "knots" -> "knots"
                    "hpa", "mbar" -> "hectopascals"
                    "m", "meters" -> "metres"
                    else -> unit
                }
            }
            unitMatcher.appendReplacement(sbUnit, "$num $unitSpoken")
        }
        unitMatcher.appendTail(sbUnit)
        return sbUnit.toString()
    }

    private fun expandNumbers(text: String, lang: SupportedLanguage): String {
        val forms = LanguagePacks.get(lang)
        var result = text

        val decMatcher = REGEX_DECIMAL.matcher(result)
        val sbDec = StringBuffer()
        while (decMatcher.find()) {
            val intPart = decMatcher.group(1)!!.toLongOrNull() ?: 0L
            val fracPartStr = decMatcher.group(2)!!
            val intWords = numberToIndianWords(intPart, forms)
            val fracWords = fracPartStr.map { ch -> forms.units[ch - '0'] }.joinToString(" ")
            decMatcher.appendReplacement(sbDec, "$intWords ${forms.decimalPointWord} $fracWords")
        }
        decMatcher.appendTail(sbDec)
        result = sbDec.toString()

        val cardMatcher = REGEX_CARDINAL.matcher(result)
        val sbCard = StringBuffer()
        while (cardMatcher.find()) {
            val cleanDigits = cardMatcher.group(1)!!.replace(",", "")
            val num = cleanDigits.toLongOrNull()
            if (num != null) {
                cardMatcher.appendReplacement(sbCard, numberToIndianWords(num, forms))
            } else {
                cardMatcher.appendReplacement(sbCard, cardMatcher.group(0)!!)
            }
        }
        cardMatcher.appendTail(sbCard)
        return sbCard.toString()
    }

    fun numberToIndianWords(n: Long, forms: LanguageNumberForms): String {
        if (n == 0L) return forms.zero
        if (n < 0) return "minus " + numberToIndianWords(-n, forms)

        val parts = ArrayList<String>(6)
        val arab = n / 100_00_00_000L
        var rem = n % 100_00_00_000L

        val crore = rem / 1_00_00_000L
        rem %= 1_00_00_000L

        val lakh = rem / 1_00_000L
        rem %= 1_00_000L

        val thousand = rem / 1_000L
        rem %= 1_000L

        val hundred = rem / 100L
        rem %= 100L

        if (arab > 0) parts.add("${numberToIndianWords(arab, forms)} ${forms.arab}")
        if (crore > 0) parts.add("${numberToIndianWords(crore, forms)} ${forms.crore}")
        if (lakh > 0) parts.add("${numberToIndianWords(lakh, forms)} ${forms.lakh}")
        if (thousand > 0) parts.add("${numberToIndianWords(thousand, forms)} ${forms.thousand}")
        if (hundred > 0) {
            val hUnit = forms.units[hundred.toInt()]
            parts.add("$hUnit ${forms.hundred}")
        }
        if (rem > 0) parts.add(forms.units[rem.toInt()])

        return parts.joinToString(" ")
    }

    private fun finalizeProsody(text: String): String {
        var cleaned = REGEX_WHITESPACE.matcher(text.trim()).replaceAll(" ")
        cleaned = cleaned.replace("!", "! , ")
        return cleaned
    }
}
