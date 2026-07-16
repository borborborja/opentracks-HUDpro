package cat.rumb.app.viewer

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import cat.rumb.app.R

/**
 * ViewPager2 adapter that shows exactly two pre-built, full-screen pages (map + data). Using real
 * views (not a Compose pager) keeps the MapLibre MapView measured and alive correctly across swipes.
 */
class ViewerPagesAdapter(private val pages: List<View>) : RecyclerView.Adapter<ViewerPagesAdapter.PageHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = pages.size
    override fun getItemViewType(position: Int) = position
    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = pages[viewType]
        (view.parent as? ViewGroup)?.removeView(view)
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {}

    class PageHolder(view: View) : RecyclerView.ViewHolder(view)
}

/**
 * Top switcher: segmented "Mapa" / "Dades", a sport quick-access icon (opens the sport picker; the
 * chosen sport drives the per-sport HUD & Dades layout), then a trailing gear opening quick-settings.
 * [sportEnabled] false = greyed and inert (the sport is locked while recording).
 */
@Composable
fun ViewerSwitcher(
    currentPage: Int,
    onSelect: (Int) -> Unit,
    onGear: () -> Unit,
    sportIcon: ImageVector,
    sportEnabled: Boolean,
    onSport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x99000000))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwitchTab(stringResource(R.string.viewer_tab_map), currentPage == 0) { onSelect(0) }
        SwitchTab(stringResource(R.string.viewer_tab_data), currentPage == 1) { onSelect(1) }
        Icon(
            sportIcon,
            contentDescription = stringResource(R.string.viewer_qs_sport),
            tint = if (sportEnabled) Color.White else Color(0x66FFFFFF),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(if (sportEnabled) Modifier.clickable(onClick = onSport) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .size(22.dp),
        )
        Icon(
            Icons.Filled.Settings,
            contentDescription = stringResource(R.string.viewer_cd_settings),
            tint = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onGear)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .size(22.dp),
        )
    }
}

@Composable
private fun SwitchTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    )
}
