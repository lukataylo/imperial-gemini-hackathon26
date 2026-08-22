package com.crusty.llm

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

class CrustyTools : ToolSet {
    @Tool(
        description = "Decide what access, if any, to give the user right now. " +
                "Call this exactly once, after you have understood why they want in."
    )
    fun proposeAccess(
        @ToolParam(description = "One of: grant, deny, counter, conditional")
        verdict: String,
        @ToolParam(description = "Minutes of access. 0 for deny. Be stingy; you can always be asked again.")
        minutes: Int,
        @ToolParam(description = "One of: full, grayscale, delayed. Prefer grayscale when the stated need is a specific task rather than browsing.")
        mode: String = "full",
        @ToolParam(description = "One short sentence to the user explaining your decision, in the second person.")
        rationale: String,
        @ToolParam(description = "What the user is committing to, in their own words. Quoted back to them next time.")
        promise: String = "",
    ): Map<String, Any> = mapOf("ok" to true)
}
