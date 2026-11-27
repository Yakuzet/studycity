package com.derscalisma.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.FocusSummary
import com.derscalisma.app.ui.LabeledControl
import com.derscalisma.app.ui.Stepper

/** Oturum bitince: bu oturumda kaç sayfa / soru / konu yapıldığını sorar. */
@Composable
fun SessionLogDialog(data: AppData, summary: SessionSummary) {
    val values = remember(summary.endedAt) { mutableStateMapOf<String, Int>() }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("🎉 Oturum bitti!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${Time.duration(summary.workMs)} çalıştın. Bu oturumda ne kadar ilerledin?")
                if (summary.focusYes + summary.focusNo > 0) {
                    FocusSummary("Oturum dikkati", summary.focusYes, summary.focusNo)
                }
                data.deadlines.filter { it.linkedGoalId == null && it.completedAt == null }.forEach { dl ->
                    LabeledControl("📅 ${dl.title}", stackBelow = 380.dp) {
                        Stepper(values["dl:${dl.id}"] ?: 0, { values["dl:${dl.id}"] = it }, min = 0, max = 2000, step = 1, bigStep = 5)
                    }
                }
                data.goals.forEach { g ->
                    LabeledControl("${g.type.emoji} ${g.name}", stackBelow = 380.dp) {
                        Stepper(
                            values[g.id] ?: 0,
                            { values[g.id] = it },
                            min = 0, max = 2000, step = 1,
                            bigStep = g.type.step.coerceAtLeast(5).takeIf { g.type != GoalType.TOPICS },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Repo.logSession(values.toMap()) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = { Repo.dismissSession() }) { Text("Geç") } },
    )
}
