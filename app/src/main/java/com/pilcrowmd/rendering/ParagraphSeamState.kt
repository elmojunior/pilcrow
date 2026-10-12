// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.rendering

import com.pilcrowmd.domain.markdown.ParagraphSeams

/**
 * The seams of the shown document's split paragraphs (M-328), read by [ProseBlockEntry] at bind time
 * so the pieces of one paragraph are drawn with no gap between them. The reader loads it with each
 * model, like [DetailsState]; empty until then.
 */
class ParagraphSeamState {
    var seams: ParagraphSeams = ParagraphSeams.NONE
}
