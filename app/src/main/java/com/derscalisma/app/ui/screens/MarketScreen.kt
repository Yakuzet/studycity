@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

private val marketCategories = ItemCategory.entries.filter { it != ItemCategory.GROUND || CITY_ENABLED }

@Composable
fun MarketScreen(data: AppData) {
    var category by rememberSaveableCategory()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                marketCategories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.title) })
                }
            }
            if (!LocalLayout.current.compact) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "🪙 ${data.coins}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(Catalog.items.filter { it.category == category }, key = { it.id }) { item ->
                MarketCard(item, data)
            }
        }
    }
}

@Composable
private fun rememberSaveableCategory(): MutableState<ItemCategory> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ItemCategory.BACKGROUND) }

private fun isEquipped(data: AppData, item: MarketItem) = when (item.category) {
    ItemCategory.BACKGROUND -> data.background == item.id
    ItemCategory.THEME -> data.theme == item.id
    ItemCategory.GROUND -> data.ground == item.id
    ItemCategory.TIMER -> data.timerStyle == item.id
    ItemCategory.BOOST -> false
}

@Composable
private fun MarketCard(item: MarketItem, data: AppData) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Preview(item) }
        Spacer(Modifier.height(8.dp))
        Text("${item.emoji} ${item.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.description,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp),
        )
        Catalog.background(item.id)?.let { bg ->
            Text(
                "📷 ${bg.photographer} · Unsplash",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))

        val owned = item.id in data.owned || item.price == 0
        if (item.id == FREEZE_ID) {
            Text("Sahip: ${data.streakFreezes} / $MAX_FREEZES", style = MaterialTheme.typography.labelMedium)
            Button(
                onClick = { Repo.buy(item.id)?.let(Repo::toast) },
                enabled = data.streakFreezes < MAX_FREEZES,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("${item.price} 🪙 Al", maxLines = 1) }
        } else if (isEquipped(data, item)) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("✓ Kullanılıyor", maxLines = 1) }
        } else if (owned) {
            FilledTonalButton(onClick = { Repo.equip(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Kullan", maxLines = 1) }
        } else {
            Button(onClick = { Repo.buy(item.id)?.let(Repo::toast) }, modifier = Modifier.fillMaxWidth()) {
                Text("${item.price} 🪙 Al", maxLines = 1)
            }
        }
    }
}

@Composable
private fun Preview(item: MarketItem) {
    when (item.category) {
        ItemCategory.BACKGROUND -> {
            val bg = Catalog.background(item.id)
            if (bg != null) {
                Image(
                    painter = painterResource(bg.thumb),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text("Düz renk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ItemCategory.THEME -> {
            val t = Catalog.theme(item.id)
            Row(
                Modifier.fillMaxSize().background(Color(t.background)).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(t.surface)))
                listOf(t.primary, t.secondary, t.tertiary).forEach { c ->
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)))
                }
            }
        }
        ItemCategory.GROUND -> {
            val g = Catalog.ground(item.id)
            Column(Modifier.fillMaxSize()) {
                for (y in 0 until 3) {
                    Row(Modifier.weight(1f)) {
                        for (x in 0 until 8) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(Color(if ((x + y) % 2 == 0) g.base else g.alt)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (x == 2 && y == 1) Text("🏠", fontSize = 14.sp)
                                if (x == 5 && y == 1) Text("🌳", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        ItemCategory.TIMER -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = 0.68f,
                    modifier = Modifier.size(60.dp),
                    strokeWidth = 6.dp,
                    style = Catalog.timerStyle(item.id),
                )
            }
        }
        ItemCategory.BOOST -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text(item.emoji, fontSize = 40.sp)
            }
        }
    }
}
