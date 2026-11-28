package com.ramkotimartha.expensetracker.ai

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import kotlin.math.max
import kotlin.math.min

enum class SuggestionSource {
    HISTORY,
    TAG_NAME,
    KEYWORD
}

data class TagSuggestion(
    val tag: Tag,
    val confidence: Float,
    val source: SuggestionSource
)

/**
 * 100% on-device, privacy-preserving tag suggestion engine.
 *
 * Combines three signals in priority order:
 * 1. Personal transaction history learning (exact note & token specificity associations)
 * 2. Direct, stemmed, and typo-tolerant matching against existing tag names (including custom tags)
 * 3. Built-in semantic keyword lexicon (global + India-contextual merchants and terms)
 */
object SmartTagSuggester {

    private const val MIN_CONFIDENCE = 0.55f

    private val STOP_WORDS = setOf(
        "a", "an", "and", "at", "by", "for", "from", "in", "into", "is", "it",
        "my", "of", "on", "or", "our", "paid", "pay", "payment", "received",
        "rs", "inr", "spent", "the", "to", "via", "with"
    )

    private data class SemanticCluster(
        val type: TransactionType,
        val preferredTagAliases: List<String>,
        val phrases: Set<String> = emptySet(),
        val keywords: Set<String>
    )

    private val SEMANTIC_CLUSTERS = listOf(
        // --- EXPENSE CLUSTERS ---
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("coffee", "cafe", "tea", "snacks", "dining", "food"),
            phrases = setOf("blue tokai", "third wave", "tim hortons", "ccd", "chai point", "chaayos"),
            keywords = setOf(
                "coffee", "cafe", "chai", "tea", "starbucks", "espresso", "latte",
                "cappuccino", "mocha", "americano", "bakery"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("dining", "restaurants", "restaurant", "eat out", "food"),
            phrases = setOf("burger king", "taco bell", "pizza hut", "barbeque nation", "ice cream", "street food"),
            keywords = setOf(
                "zomato", "swiggy", "eatclub", "eatsure", "lunch", "dinner", "breakfast",
                "brunch", "snack", "snacks", "meal", "meals", "restaurant", "dining",
                "eat", "eating", "takeout", "takeaway", "pizza", "burger", "biryani",
                "dosa", "idli", "samosa", "paratha", "momos", "thali", "sandwich",
                "pasta", "noodles", "sushi", "shawarma", "mcdonalds", "kfc", "dominos",
                "subway", "haldirams", "canteen", "mess", "tiffin", "dessert", "cake",
                "pastry", "chocolate", "drinks", "juice", "smoothie"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("groceries", "grocery", "supermarket", "provisions", "food"),
            phrases = setOf("big basket", "water can", "mother dairy", "nature basket", "reliance fresh", "star bazaar"),
            keywords = setOf(
                "blinkit", "zepto", "instamart", "bigbasket", "dmart", "jiomart",
                "supermarket", "hypermarket", "grocery", "groceries", "kirana", "provisions",
                "ration", "milk", "curd", "yogurt", "paneer", "butter", "cheese", "dairy",
                "bread", "eggs", "vegetables", "veggies", "fruits", "apple", "banana",
                "onion", "potato", "tomato", "rice", "atta", "flour", "dal", "pulses",
                "spices", "masala", "sugar", "salt", "cereal", "oats", "meat", "chicken",
                "mutton", "fish", "seafood"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("commute", "cab", "transit", "transport", "transportation", "travel"),
            phrases = setOf("namma yatri", "uber auto", "ola cab", "metro card", "bus pass", "local train"),
            keywords = setOf(
                "uber", "ola", "rapido", "blu", "blusmart", "meru", "cab", "taxi",
                "auto", "rickshaw", "autorickshaw", "metro", "bus", "shuttle", "commute",
                "transit", "fare", "toll", "fastag", "parking"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("fuel", "petrol", "vehicle", "car", "bike", "transport", "transportation"),
            phrases = setOf("indian oil", "bharat petroleum", "hp petrol", "car wash", "ev charging", "air check"),
            keywords = setOf(
                "petrol", "diesel", "fuel", "cng", "gasoline", "shell", "servicing",
                "mechanic", "puncture", "tyre", "scooter"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("travel", "vacation", "trips", "holidays", "transport", "transportation"),
            phrases = setOf("air india", "make my trip", "train ticket", "flight ticket"),
            keywords = setOf(
                "flight", "flights", "airline", "airport", "indigo", "vistara", "spicejet",
                "akasa", "irctc", "railway", "train", "redbus", "makemytrip", "goibibo",
                "cleartrip", "ixigo", "agoda", "booking", "airbnb", "oyo", "hotel",
                "resort", "hostel", "vacation", "holiday", "trip", "visa", "passport"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("rent", "housing", "home", "bills", "bill"),
            phrases = setOf("house rent", "flat rent", "room rent", "pg rent", "security deposit"),
            keywords = setOf("rent", "landlord", "lease", "brokerage", "hostel")
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("subscriptions", "subscription", "streaming", "entertainment", "bills", "bill"),
            phrases = setOf("amazon prime", "prime video", "apple music", "google one", "youtube premium", "disney hotstar"),
            keywords = setOf(
                "netflix", "spotify", "hotstar", "jiocinema", "sonyliv", "zee5",
                "youtube", "icloud", "chatgpt", "openai", "claude", "midjourney",
                "github", "audible", "kindle", "canva", "adobe", "dropbox",
                "subscription", "subscriptions", "membership"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("utilities", "utility", "bills", "bill"),
            phrases = setOf("power bill", "water bill", "gas cylinder", "mobile bill", "credit card", "tata play", "act fibernet"),
            keywords = setOf(
                "electricity", "electric", "power", "bescom", "mseb", "water", "sewage",
                "gas", "cylinder", "lpg", "indane", "bharatgas", "piped", "wifi",
                "broadband", "internet", "fiber", "router", "jio", "airtel", "vodafone",
                "bsnl", "recharge", "prepaid", "postpaid", "mobile", "phonebill", "dth",
                "maintenance", "society", "emi", "loan", "mortgage", "insurance", "lic",
                "premium", "tax", "taxes", "utility", "utilities", "bill", "bills"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("gifts", "gift", "shopping"),
            phrases = setOf("birthday gift", "wedding gift", "anniversary gift"),
            keywords = setOf("gift", "gifts", "present", "bouquet", "flowers", "ferns")
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("personal care", "beauty", "grooming", "salon", "shopping", "health"),
            phrases = setOf("hair cut", "urban company", "skin care"),
            keywords = setOf(
                "salon", "haircut", "barber", "spa", "facial", "grooming", "nykaa",
                "skincare", "cosmetics", "makeup", "lipstick", "sunscreen", "moisturizer",
                "shampoo", "conditioner", "soap", "perfume", "deodorant", "toiletries"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("electronics", "gadgets", "tech", "shopping"),
            phrases = setOf("reliance digital", "vijay sales", "apple store"),
            keywords = setOf(
                "croma", "electronics", "gadget", "gadgets", "phone", "smartphone",
                "iphone", "samsung", "pixel", "laptop", "macbook", "ipad", "earbuds",
                "airpods", "headphones", "speaker", "charger", "adapter", "cable",
                "powerbank", "keyboard", "mouse", "monitor", "camera", "pendrive", "ssd"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("shopping", "clothes", "apparel", "fashion"),
            phrases = setOf("tata cliq", "home decor", "lifestyle store"),
            keywords = setOf(
                "amazon", "flipkart", "myntra", "ajio", "meesho", "zara", "uniqlo",
                "westside", "pantaloons", "zudio", "max", "decathlon", "ikea", "pepperfry",
                "shopping", "mall", "clothes", "clothing", "apparel", "shirt", "tshirt",
                "top", "jeans", "trousers", "pants", "shorts", "dress", "kurta", "saree",
                "jacket", "hoodie", "sweater", "shoes", "sneakers", "sandals", "slippers",
                "socks", "watch", "bag", "backpack", "luggage", "wallet", "belt",
                "sunglasses", "furniture", "mattress", "curtains", "utensils", "kitchenware"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("fitness", "gym", "sports", "workout", "health", "healthcare"),
            phrases = setOf("cult fit", "gold gym", "whey protein"),
            keywords = setOf(
                "gym", "cult", "cultfit", "fitness", "workout", "crossfit", "yoga",
                "pilates", "zumba", "swimming", "badminton", "tennis", "cricket",
                "football", "turf", "protein", "creatine", "supplement", "supplements"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("medical", "medicine", "pharmacy", "doctor", "health", "healthcare"),
            phrases = setOf("apollo pharmacy", "tata 1mg", "blood test", "lab test", "eye checkup", "root canal"),
            keywords = setOf(
                "pharmacy", "chemist", "medical", "medicine", "medicines", "meds",
                "tablet", "tablets", "pills", "capsule", "syrup", "ointment", "antibiotic",
                "painkiller", "paracetamol", "vitamin", "vitamins", "apollo", "pharmeasy",
                "1mg", "netmeds", "medplus", "doctor", "physician", "consultation",
                "hospital", "clinic", "nursing", "dentist", "dental", "dermatologist",
                "pediatrician", "orthopedic", "ophthalmologist", "lenskart", "spectacles",
                "glasses", "contacts", "checkup", "diagnostics", "pathology", "xray",
                "mri", "ctscan", "ultrasound", "vaccine", "vaccination", "therapy",
                "physiotherapy", "health", "healthcare", "firstaid", "bandage"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("education", "study", "books", "courses", "shopping"),
            phrases = setOf("online course", "school fee", "college fee", "tuition fee"),
            keywords = setOf(
                "education", "tuition", "coaching", "school", "college", "university",
                "exam", "udemy", "coursera", "edx", "unacademy", "book", "books",
                "textbook", "novel", "stationery", "notebook", "pen", "workshop", "seminar"
            )
        ),
        SemanticCluster(
            type = TransactionType.EXPENSE,
            preferredTagAliases = listOf("entertainment", "movies", "leisure", "fun", "other"),
            phrases = setOf("book my show", "movie ticket", "amusement park"),
            keywords = setOf(
                "movie", "movies", "cinema", "theatre", "pvr", "inox", "bookmyshow",
                "concert", "show", "comedy", "bowling", "arcade", "gaming", "playstation",
                "steam", "museum", "zoo", "entertainment"
            )
        ),

        // --- INCOME CLUSTERS ---
        SemanticCluster(
            type = TransactionType.INCOME,
            preferredTagAliases = listOf("salary", "paycheck", "wages", "stipend", "bonus"),
            phrases = setOf("monthly pay", "performance bonus", "joining bonus"),
            keywords = setOf(
                "salary", "paycheck", "payslip", "payroll", "wages", "stipend",
                "bonus", "incentive", "appraisal", "arrears", "employer"
            )
        ),
        SemanticCluster(
            type = TransactionType.INCOME,
            preferredTagAliases = listOf("freelance", "consulting", "business", "client", "other"),
            phrases = setOf("client payment", "side hustle", "project fee"),
            keywords = setOf(
                "freelance", "freelancing", "upwork", "fiverr", "toptal", "client",
                "consulting", "consultation", "contract", "invoice", "gig", "commission",
                "royalty", "business"
            )
        ),
        SemanticCluster(
            type = TransactionType.INCOME,
            preferredTagAliases = listOf("investments", "investment", "dividends", "dividend", "interest", "returns", "other"),
            phrases = setOf("mutual fund", "fixed deposit", "stock profit", "capital gain", "rental income"),
            keywords = setOf(
                "dividend", "dividends", "interest", "zerodha", "groww", "upstox",
                "stocks", "shares", "equity", "profit", "maturity", "coupon", "yield"
            )
        ),
        SemanticCluster(
            type = TransactionType.INCOME,
            preferredTagAliases = listOf("refund", "refunds", "cashback", "reimbursement", "gifts", "other"),
            phrases = setOf("pocket money", "tax refund", "expense reimbursement"),
            keywords = setOf(
                "refund", "refunded", "cashback", "reward", "rewards", "reimbursement",
                "reimbursed", "gift", "allowance", "sold", "resale", "settlement",
                "return", "misc", "miscellaneous", "other"
            )
        )
    )

    fun suggestTag(
        note: String,
        type: TransactionType,
        availableTags: List<Tag>,
        pastTransactions: List<TransactionWithTag> = emptyList()
    ): TagSuggestion? {
        val candidates = availableTags.filter { it.type == type }
        if (candidates.isEmpty()) return null

        val normalizedNote = normalizeText(note)
        if (normalizedNote.length < 2) return null

        val noteTokens = tokenize(normalizedNote)
        if (noteTokens.isEmpty()) return null

        val historyMatch = scoreFromHistory(normalizedNote, noteTokens, type, candidates, pastTransactions)
        val tagNameMatch = scoreFromTagNames(normalizedNote, noteTokens, candidates)
        val keywordMatch = scoreFromKeywords(normalizedNote, noteTokens, type, candidates)

        // Personal history takes top priority when confident so user habits always win
        if (historyMatch != null && historyMatch.confidence >= 0.72f) {
            return historyMatch
        }

        // Combine candidates across signals, giving a slight consensus boost when signals agree
        val bestByTagId = mutableMapOf<Long, TagSuggestion>()
        listOfNotNull(historyMatch, tagNameMatch, keywordMatch).forEach { suggestion ->
            val existing = bestByTagId[suggestion.tag.id]
            if (existing == null) {
                bestByTagId[suggestion.tag.id] = suggestion
            } else {
                val primary = if (suggestion.confidence > existing.confidence) suggestion else existing
                val boosted = (primary.confidence + 0.08f).coerceAtMost(0.99f)
                bestByTagId[suggestion.tag.id] = primary.copy(confidence = boosted)
            }
        }

        val winner = bestByTagId.values.maxByOrNull { it.confidence } ?: return null
        return if (winner.confidence >= MIN_CONFIDENCE) winner else null
    }

    private fun scoreFromHistory(
        normalizedNote: String,
        noteTokens: List<String>,
        type: TransactionType,
        candidates: List<Tag>,
        pastTransactions: List<TransactionWithTag>
    ): TagSuggestion? {
        val candidateById = candidates.associateBy { it.id }
        val relevantHistory = pastTransactions.filter {
            it.transaction.type == type &&
                !it.transaction.note.isNullOrBlank() &&
                candidateById.containsKey(it.tag.id)
        }
        if (relevantHistory.isEmpty()) return null

        // Precompute how many distinct tags each historical token appears in (specificity / IDF)
        val tokenTagSet = mutableMapOf<String, MutableSet<Long>>()
        val parsedHistory = relevantHistory.mapIndexed { index, item ->
            val norm = normalizeText(item.transaction.note.orEmpty())
            val toks = tokenize(norm)
            toks.forEach { tok ->
                tokenTagSet.getOrPut(tok) { mutableSetOf() }.add(item.tag.id)
            }
            Triple(item, norm, toks) to index
        }

        val rawScores = mutableMapOf<Long, Float>()
        var exactMatchTagId: Long? = null

        for ((entry, index) in parsedHistory) {
            val (item, histNorm, histTokens) = entry
            if (histNorm.isEmpty()) continue

            val tagId = item.tag.id
            // Slight recency preference (pastTransactions is ordered newest first)
            val recencyWeight = 1f / (1f + index * 0.05f)

            if (histNorm == normalizedNote) {
                if (exactMatchTagId == null) exactMatchTagId = tagId
                rawScores[tagId] = (rawScores[tagId] ?: 0f) + 3.0f * recencyWeight
                continue
            }

            if (normalizedNote.length >= 4 && histNorm.length >= 4 &&
                (histNorm.contains(normalizedNote) || normalizedNote.contains(histNorm))
            ) {
                rawScores[tagId] = (rawScores[tagId] ?: 0f) + 1.4f * recencyWeight
            }

            for (token in noteTokens) {
                val exactTokenHit = token in histTokens
                val prefixTokenHit = !exactTokenHit && token.length >= 4 &&
                    histTokens.any { it.startsWith(token) || (it.length >= 4 && token.startsWith(it)) }

                if (exactTokenHit || prefixTokenHit) {
                    val tagSpread = tokenTagSet[token]?.size ?: 1
                    val specificity = 1f / tagSpread.toFloat()
                    val matchQuality = if (exactTokenHit) 1.0f else 0.7f
                    rawScores[tagId] = (rawScores[tagId] ?: 0f) + (1.15f * specificity * matchQuality * recencyWeight)
                }
            }
        }

        val topEntry = rawScores.maxByOrNull { it.value } ?: return null
        val topTag = candidateById[topEntry.key] ?: return null
        val totalScore = rawScores.values.sum().coerceAtLeast(0.01f)
        val dominance = topEntry.value / totalScore

        val confidence = when {
            exactMatchTagId == topTag.id -> 0.95f
            topEntry.value >= 1.8f && dominance >= 0.6f -> 0.86f
            topEntry.value >= 0.9f && dominance >= 0.65f -> 0.76f
            topEntry.value >= 0.65f && dominance >= 0.7f -> 0.64f
            else -> 0f
        }

        return if (confidence >= MIN_CONFIDENCE) {
            TagSuggestion(tag = topTag, confidence = confidence, source = SuggestionSource.HISTORY)
        } else {
            null
        }
    }

    private fun scoreFromTagNames(
        normalizedNote: String,
        noteTokens: List<String>,
        candidates: List<Tag>
    ): TagSuggestion? {
        var bestTag: Tag? = null
        var bestScore = 0f

        val noteStems = noteTokens.map { stem(it) }

        for (tag in candidates) {
            val normTag = normalizeText(tag.name)
            if (normTag.isEmpty()) continue
            val tagTokens = tokenize(normTag).ifEmpty { normTag.split(" ").filter { it.isNotBlank() } }
            val tagStems = tagTokens.map { stem(it) }

            val score = when {
                normalizedNote == normTag -> 0.93f
                normTag.length >= 3 && containsPhrase(normalizedNote, normTag) -> 0.90f
                tagTokens.any { it.length >= 3 && it in noteTokens } -> 0.88f
                tagStems.any { it.length >= 3 && it in noteStems } -> 0.84f
                noteTokens.any { nt -> nt.length >= 4 && tagTokens.any { tt -> tt.startsWith(nt) } } -> 0.74f
                noteTokens.any { nt ->
                    nt.length >= 5 && tagTokens.any { tt -> tt.length >= 5 && isWithinEditDistanceOne(nt, tt) }
                } -> 0.72f
                else -> 0f
            }

            if (score > bestScore) {
                bestScore = score
                bestTag = tag
            }
        }

        return if (bestTag != null && bestScore >= MIN_CONFIDENCE) {
            TagSuggestion(tag = bestTag, confidence = bestScore, source = SuggestionSource.TAG_NAME)
        } else {
            null
        }
    }

    private fun scoreFromKeywords(
        normalizedNote: String,
        noteTokens: List<String>,
        type: TransactionType,
        candidates: List<Tag>
    ): TagSuggestion? {
        val noteStems = noteTokens.map { stem(it) }

        var bestTag: Tag? = null
        var bestConfidence = 0f

        for (cluster in SEMANTIC_CLUSTERS) {
            if (cluster.type != type) continue

            val resolvedTag = resolvePreferredTag(cluster.preferredTagAliases, candidates) ?: continue

            var clusterStrength = 0f

            for (phrase in cluster.phrases) {
                if (containsPhrase(normalizedNote, phrase)) {
                    clusterStrength = max(clusterStrength, 0.90f)
                }
            }

            var matchedTokens = 0
            for (i in noteTokens.indices) {
                val token = noteTokens[i]
                val tokenStem = noteStems[i]

                val tokenStrength = when {
                    token in cluster.keywords -> 0.85f
                    tokenStem.length >= 4 && cluster.keywords.any { stem(it) == tokenStem } -> 0.80f
                    token.length >= 4 && cluster.keywords.any { it.startsWith(token) } -> 0.68f
                    token.length >= 5 && cluster.keywords.any {
                        it.length >= 5 && isWithinEditDistanceOne(token, it)
                    } -> 0.70f
                    else -> 0f
                }

                if (tokenStrength > 0f) {
                    matchedTokens++
                    clusterStrength = max(clusterStrength, tokenStrength)
                }
            }

            if (matchedTokens >= 2) {
                clusterStrength = (clusterStrength + 0.06f).coerceAtMost(0.94f)
            }

            if (clusterStrength > bestConfidence) {
                bestConfidence = clusterStrength
                bestTag = resolvedTag
            }
        }

        return if (bestTag != null && bestConfidence >= MIN_CONFIDENCE) {
            TagSuggestion(tag = bestTag, confidence = bestConfidence, source = SuggestionSource.KEYWORD)
        } else {
            null
        }
    }

    /**
     * Resolves the highest-priority tag from [preferredTagAliases] present in [candidates].
     * Allows fine-grained custom tags (e.g., "Coffee" or "Subscriptions") to take priority
     * over broader default tags (e.g., "Food" or "Bills").
     */
    private fun resolvePreferredTag(
        preferredTagAliases: List<String>,
        candidates: List<Tag>
    ): Tag? {
        val normalizedCandidates = candidates.map { tag ->
            val norm = normalizeText(tag.name)
            val stems = norm.split(" ").filter { it.isNotBlank() }.map { stem(it) }.toSet()
            Triple(tag, norm, stems)
        }

        for (alias in preferredTagAliases) {
            val normAlias = normalizeText(alias)
            val aliasStem = stem(normAlias)
            val match = normalizedCandidates.firstOrNull { (_, candidateNorm, candidateStems) ->
                candidateNorm == normAlias ||
                    (aliasStem.length >= 3 && aliasStem in candidateStems)
            }
            if (match != null) return match.first
        }
        return null
    }

    internal fun normalizeText(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenize(normalized: String): List<String> {
        if (normalized.isEmpty()) return emptyList()
        return normalized
            .split(" ")
            .filter { it.length >= 2 && it !in STOP_WORDS && !it.all { ch -> ch.isDigit() } }
    }

    private fun containsPhrase(normalizedText: String, phrase: String): Boolean {
        return Regex("\\b${Regex.escape(phrase)}\\b").containsMatchIn(normalizedText)
    }

    private fun stem(word: String): String {
        if (word.length <= 3) return word
        return when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("ing") && word.length > 5 -> {
                val base = word.dropLast(3)
                if (base.length >= 3 && base.last() == base[base.length - 2]) base.dropLast(1) else base
            }
            word.endsWith("es") && word.length > 4 &&
                (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses")) ->
                word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> word.dropLast(1)
            else -> word
        }
    }

    private fun isWithinEditDistanceOne(a: String, b: String): Boolean {
        val lenDiff = kotlin.math.abs(a.length - b.length)
        if (lenDiff > 1) return false
        if (a == b) return true

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            var rowMin = dp[0]
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
                rowMin = min(rowMin, dp[j])
            }
            if (rowMin > 1) return false
        }
        return dp[b.length] <= 1
    }
}
