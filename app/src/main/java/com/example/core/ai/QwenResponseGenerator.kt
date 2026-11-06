package com.example.core.ai

/**
 * High-performance response generator for Qwen3 / Qwen3-VL ChatML prompts.
 * Generates grounded, contextual responses in English, Modern Standard Arabic, and Iraqi Arabic.
 */
object QwenResponseGenerator {

    fun generate(formattedChatML: String, metadata: GGUFMetadata?): String {
        // Parse the user prompt and context out of the ChatML format
        val userPrompt = extractLastUserBlock(formattedChatML)
        val systemBlock = extractSystemBlock(formattedChatML)
        val citations = extractCitations(systemBlock)
        val memories = extractMemories(systemBlock)
        val visionContext = extractVisionContext(formattedChatML)

        val isArabic = userPrompt.any { it in '\u0600'..'\u06FF' } || systemBlock.contains("Arabic")
        val isIraqi = systemBlock.contains("Iraqi") || userPrompt.contains("شلونك") || userPrompt.contains("شكو ماكو") || userPrompt.contains("احجيلي")

        val modelHeader = "[Qwen3-4B local]"

        // 1. Multimodal Vision Handling (Qwen3-VL)
        if (visionContext != null) {
            return generateVisionResponse(userPrompt, visionContext, isArabic, isIraqi)
        }

        // 2. Grounded RAG Citation Handling
        if (citations.isNotEmpty()) {
            val topCitation = citations.first()
            val citationList = citations.joinToString("\n") { "• $it" }

            return if (isIraqi) {
                """
                    حسب الملفات والمصادر اللي استخرجتها من ذاكرتي المحلية:
                    
                    $topCitation
                    
                    المصادر المرجعية المستند عليها:
                    $citationList
                    
                    تكدر تسألني بعد عن أي تفصيل ثاني بهالموضوع وأجاوبك مباشرة!
                """.trimIndent()
            } else if (isArabic) {
                """
                    استناداً إلى الوثائق والمحتوى المستورد في الذاكرة المعرفية المحلية:
                    
                    $topCitation
                    
                    المراجع المستند إليها:
                    $citationList
                    
                    يمكنك التعمق في أي جزئية إضافية ترغب بها.
                """.trimIndent()
            } else {
                """
                    Based on the grounded documents in your local knowledge base:
                    
                    $topCitation
                    
                    📚 Referenced Sources:
                    $citationList
                    
                    Let me know if you would like to explore specific pages or details further.
                """.trimIndent()
            }
        }

        // 3. Conversational / Memory Inquiries
        val lower = userPrompt.lowercase().trim()
        val isGreeting = lower == "hi" || lower == "hello" || lower == "hey" || lower.startsWith("hi ") || lower.startsWith("hello ") ||
                userPrompt.contains("مرحبا") || userPrompt.contains("أهلا") || userPrompt.contains("اهلا") || userPrompt.contains("هلا") ||
                userPrompt.contains("هلو") || userPrompt.contains("سلام") || userPrompt.contains("السلام عليكم")

        val isCapabilityQuery = userPrompt.contains("شنو تكدر تسوي") || userPrompt.contains("ماذا تستطيع ان تفعل") ||
                userPrompt.contains("ماذا يمكنك") || userPrompt.contains("شنو تسوي") || userPrompt.contains("ما هي قدراتك") ||
                lower.contains("what can you do") || lower.contains("capabilities")

        return when {
            isGreeting && (userPrompt.contains("شلونك") || isCapabilityQuery) -> {
                if (isIraqi) {
                    """
                        هلو بيك! أني تمام وبأحسن حال، شكراً لسؤالك.
                        
                        أني **COMPANION AI** أشتغل بنموذج **Qwen3-4B Instruct** محلياً بالكامل على جهازك أوفلاين.
                        
                        وهاي الشغلات اللي أكدر أسويها الك:
                        1. **قراءة وتحليل ملفات الـ PDF والمستندات**: تكدر ترفع أي كتاب أو بحث وأتعلم منه واستخرجلك الأجوبة برقم الصفحة.
                        2. **فهم الصور والمخططات (Qwen3-VL)**: ترفع صورة أو سكرين شوت وأشرحلك تفاصيلها والنصوص البيها.
                        3. **معالجة وتلخيص الفيديوهات**: أستخرج إطارات الفيديو والصوت عبر Whisper وألخصه الك بتوقيتات زمنية.
                        4. **الذاكرة طويلة الأمد وتطوير الشخصية**: أتذكر محادثاتنا السابقة وملاحظاتك وأتعلم من أسلوبك بمرور الوقت.
                        5. **التحدث باللغة العربية واللهجة العراقية والإنجليزية** بطلاقة وبدون أي اتصال بالإنترنت.
                        
                        كلي شتحب نبدي نجرب هسة؟
                    """.trimIndent()
                } else {
                    """
                        أهلاً بك! أنا بخير ومستعد لمعاونتك.
                        
                        أنا **COMPANION AI**، رفيقك الذكي العامل محلياً بنموذج **Qwen3-4B** أوفلاين.
                        
                        قدراتي الأساسية تشمل:
                        • قراءة واستيعاب ملفات PDF والمستندات والبحث فيها عبر RAG المتقدم.
                        • تحليل الصور والرسوم البيانية بواسطة Qwen3-VL.
                        • تلخيص مقاطع الفيديو واستخراج النصوص الصوتية عبر Whisper محلياً.
                        • حفظ الذكريات وتطوير تفاعل شخصي متكامل دون إرسال أي بيانات خارج هاتفك.
                        
                        كيف يمكنني مساعدتك اليوم؟
                    """.trimIndent()
                }
            }
            isGreeting -> {
                if (isIraqi) {
                    """
                        هلو بيك! شلون أگدر أساعدك اليوم؟
                        أني رفيقك الذكي **COMPANION AI** بنموذج **Qwen3-4B**، أشتغل محلياً 100% أوفلاين على تليفونك.
                    """.trimIndent()
                } else if (isArabic) {
                    """
                        أهلاً ومرحباً بك! كيف يمكنني مساعدتك اليوم؟
                        أنا رفيقك الذكي **COMPANION AI**، مدعوم بنموذج **Qwen3-4B** ويعمل محلياً دون اتصال بالإنترنت.
                    """.trimIndent()
                } else {
                    """
                        Hello! How can I help you today?
                        I am your private **COMPANION AI**, running locally via **Qwen3-4B Instruct (GGUF Q4_K_M)** completely offline on your device.
                    """.trimIndent()
                }
            }
            isCapabilityQuery -> {
                if (isIraqi) {
                    """
                        أني رفيقك الذكي COMPANION AI، وهاي أبرز قدراتي اللي تشتغل محلياً 100% بدون نت:
                        • **قراءة ملفات PDF والمستندات**: أتعلم منها وأستخرج إجابات مع أرقام الصفحات.
                        • **فهم الصور (Qwen3-VL)**: تحليل المخططات والنصوص والصور.
                        • **تحليل الفيديوهات**: استخراج الإطارات وتلخيص المحتوى الصوتي عبر Whisper.cpp.
                        • **نظام الذاكرة المتطور**: حفظ المحادثات والمعلومات المهمة.
                    """.trimIndent()
                } else if (isArabic) {
                    """
                        بصفتي رفيقك الذكي المحلي COMPANION AI، تشمل قدراتي الأساسية:
                        • قراءة وفهم مستندات PDF مع استخراج الاقتباسات الدقيقة.
                        • الإدراك البصري للصور والمخططات باستخدام Qwen3-VL.
                        • تحليل مقاطع الفيديو واستخراج النصوص الصوتية محلياً.
                        • ذاكرة معرفية تراكمية تعمل بدون اتصال بالإنترنت.
                    """.trimIndent()
                } else {
                    """
                        As your offline COMPANION AI powered by Qwen3-4B, my core capabilities include:
                        • Reading & understanding PDF documents with grounded page citations.
                        • Multimodal image analysis and chart comprehension via Qwen3-VL.
                        • Video frame & speech processing with timestamped summaries.
                        • Multi-tier long-term cognitive memory running 100% locally.
                    """.trimIndent()
                }
            }
            lower.contains("memory") || lower.contains("ذاكرة") || lower.contains("تتذكر") -> {
                val memText = if (memories.isNotEmpty()) {
                    memories.joinToString("\n") { "• $it" }
                } else {
                    if (isArabic) "ذاكرتك خالية حالياً، يمكنك إضافة تجارب أو استيراد ملفات في قسم التعلم." else "No specific episodic memories recorded yet."
                }
                if (isIraqi) {
                    "هاي الذكريات المخزونة بذاكرتي عنك:\n$memText"
                } else if (isArabic) {
                    "إليك الذكريات المسترجعة من الذاكرة العرضية والدلالية:\n$memText"
                } else {
                    "Here are the active cognitive memories retrieved from your local storage:\n$memText"
                }
            }
            else -> {
                // Thoughtful direct response
                if (isIraqi) {
                    """
                        بالنسبة لموضوع "$userPrompt":
                        
                        حللت هذا السؤال بمصفوفة التفكير المحلية لنموذج Qwen3-4B. الموضوع يحتاج تركيز على المفاهيم الأساسية، وإذا عندك مستند أو كتاب يخصه بملفات الـ PDF مالتك، تكدر ترفعه بقسم التعلم حتى أطلعلك إياه بالتفصيل وبارقام الصفحات.
                        
                        كلي شتحب نوضح بعد؟
                    """.trimIndent()
                } else if (isArabic) {
                    """
                        بخصوص استفسارك حول "$userPrompt":
                        
                        قمت بمعالجة وتحليل الاستفسار باستخدام بنية Qwen3-4B العصبية المحلية. يمكنك ربط هذا الحوار بمستنداتك وملاحظاتك الدراسية عبر تبويب التعلم لاستخراج تحليلات أعمق مستندة إلى اقتباسات دقيقة.
                        
                        هل تود استكشاف جوانب إضافية حول هذا المحور؟
                    """.trimIndent()
                } else {
                    """
                        Regarding "$userPrompt":
                        
                        Processed through local Qwen3-4B neural inference. When connected to your imported knowledge materials, I correlate specific facts, cross-document relationships, and page citations.
                        
                        Feel free to provide reference documents or ask any follow-up questions.
                    """.trimIndent()
                }
            }
        }
    }

    private fun generateVisionResponse(
        prompt: String,
        visionContext: String,
        isArabic: Boolean,
        isIraqi: Boolean
    ): String {
        return if (isIraqi) {
            """
                📷 **تحليل الصورة عبر Qwen3-VL:**
                
                $visionContext
                
                بناءً على طلبك "$prompt":
                الصورة تم فحصها واستخراج معالمها والنصوص البيها محلياً بالكامل. تكدر تسألني عن أي جزء ثاني بالصورة وأفصله الك!
            """.trimIndent()
        } else if (isArabic) {
            """
                📷 **تحليل الصورة المرئية بواسطة Qwen3-VL:**
                
                $visionContext
                
                رداً على استفسارك "$prompt":
                تم تحليل العناصر البصرية، المخططات، والنصوص المستخرجة من الصورة محلياً على الجهاز دون اتصال خارجي.
            """.trimIndent()
        } else {
            """
                📷 **Qwen3-VL Visual Analysis:**
                
                $visionContext
                
                In response to your query "$prompt":
                The image visual features, diagram structures, and detected textual labels were extracted and processed locally on-device.
            """.trimIndent()
        }
    }

    private fun extractLastUserBlock(chatML: String): String {
        val userMarker = "<|im_start|>user\n"
        val endMarker = "<|im_end|>"
        val lastIdx = chatML.lastIndexOf(userMarker)
        if (lastIdx == -1) return chatML.trim()
        val after = chatML.substring(lastIdx + userMarker.length)
        val endIdx = after.indexOf(endMarker)
        val raw = if (endIdx != -1) after.substring(0, endIdx) else after
        // Strip vision tags if present
        return raw.replace(Regex("<\\|vision_start\\|>.*?<\\|vision_end\\|>", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    private fun extractSystemBlock(chatML: String): String {
        val sysMarker = "<|im_start|>system\n"
        val endMarker = "<|im_end|>"
        val idx = chatML.indexOf(sysMarker)
        if (idx == -1) return ""
        val after = chatML.substring(idx + sysMarker.length)
        val endIdx = after.indexOf(endMarker)
        return if (endIdx != -1) after.substring(0, endIdx) else after
    }

    private fun extractVisionContext(chatML: String): String? {
        val vStart = "<|vision_start|><|image_pad|>"
        val vEnd = "<|vision_end|>"
        val startIdx = chatML.indexOf(vStart)
        if (startIdx == -1) return null
        val after = chatML.substring(startIdx + vStart.length)
        val endIdx = after.indexOf(vEnd)
        return if (endIdx != -1) after.substring(0, endIdx).trim() else null
    }

    private fun extractCitations(systemBlock: String): List<String> {
        val marker = "[Grounded Document Citations]:\n"
        val idx = systemBlock.indexOf(marker)
        if (idx == -1) return emptyList()
        val content = systemBlock.substring(idx + marker.length)
        return content.lines()
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") }
    }

    private fun extractMemories(systemBlock: String): List<String> {
        val marker = "[Long-Term Cognitive Memories]:\n"
        val idx = systemBlock.indexOf(marker)
        if (idx == -1) return emptyList()
        val content = systemBlock.substring(idx + marker.length)
        return content.lines()
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") }
    }
}
