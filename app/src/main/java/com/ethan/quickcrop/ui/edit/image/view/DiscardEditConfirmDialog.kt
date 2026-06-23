package com.ethan.quickcrop.ui.edit.image.view

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 放弃编辑确认弹窗，用于有未保存修改时拦截返回操作。
 */
@Composable
fun DiscardEditConfirmDialog(
    onConfirmDiscard: () -> Unit,
    onContinueEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueEdit,
        containerColor = Color(0xFF18181B),
        title = {
            Text(
                text = "不保存修改？",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "当前编辑内容尚未保存，确认返回将丢失这些修改。",
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDiscard) {
                Text(text = "确认", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueEdit) {
                Text(text = "继续编辑", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
            }
        }
    )
}