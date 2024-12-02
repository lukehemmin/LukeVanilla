package com.lukehemmin.lukeVanilla.System.Discord

import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal

class SupportCaseModal {
    companion object {
        fun create(): Modal {
            val titleInput = TextInput.create("support_title", "문의 제목", TextInputStyle.SHORT)
                .setPlaceholder("문의 제목을 간단히 입력해주세요")
                .setMinLength(1)
                .setMaxLength(50)
                .build()

            val descriptionInput = TextInput.create("support_description", "문의 내용", TextInputStyle.PARAGRAPH)
                .setPlaceholder("상세한 문의 내용을 입력해주세요")
                .setMinLength(10)
                .setMaxLength(500)
                .build()

            return Modal.create("support_case_modal", "관리자 문의")
                .addActionRow(titleInput)
                .addActionRow(descriptionInput)
                .build()
        }
    }
}