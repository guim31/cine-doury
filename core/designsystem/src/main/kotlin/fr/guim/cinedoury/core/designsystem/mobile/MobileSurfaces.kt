package fr.guim.cinedoury.core.designsystem.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes

/** A tab of the bar at the bottom (`UI.md` § 9). */
@Immutable
data class MobileTab(
    val label: String,
    val icon: ImageVector,
    val tag: String,
)

/**
 * The bar at the bottom of the phone (`UI.md` § 9): Accueil, Recherche, Bibliothèques, Profil. The tab
 * of the screen is drawn in the accent; the bar sits over the navigation bar of the system.
 */
@Composable
fun MobileBottomBar(
    tabs: List<MobileTab>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    NavigationBar(modifier = modifier, containerColor = colors.surface1, contentColor = colors.text) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = CineTheme.typography.minimum, maxLines = 1) },
                modifier = Modifier.testTag(tab.tag),
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onAccent,
                        selectedTextColor = colors.accent,
                        indicatorColor = colors.accent,
                        unselectedIconColor = colors.text2,
                        unselectedTextColor = colors.text2,
                    ),
            )
        }
    }
}

/** The bar at the top of a screen pushed over a tab: « Retour », the title, its actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable () -> Unit = {},
) {
    val colors = CineTheme.colors
    TopAppBar(
        title = { Text(title, style = CineTheme.typography.title2, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { onBack?.let { MobileIconButton(CineIcons.Back, backLabel, it) } },
        actions = { actions() },
        modifier = modifier,
        windowInsets = windowInsets,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (transparent) colors.background.copy(alpha = 0f) else colors.background,
                titleContentColor = colors.text,
                navigationIconContentColor = colors.text,
                actionIconContentColor = colors.text,
            ),
    )
}

/**
 * A field typed with the keyboard of the phone (`SPEC.md` § 4.26): the label above, the error under
 * it, the action of the keyboard ([imeAction]) runs [onAction].
 */
@Composable
fun MobileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = CineTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.text3) } },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = colors.danger) } },
        singleLine = true,
        enabled = enabled,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailing,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        textStyle = CineTheme.typography.body,
        modifier = modifier.widthIn(max = MobileSizes.textFieldWidth).fillMaxWidth(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                focusedLabelColor = colors.accent,
                cursorColor = colors.accent,
                unfocusedBorderColor = colors.text3,
                unfocusedLabelColor = colors.text2,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
    )
}

/** A line of [MobileSheet]: an icon, a label, a tap. */
@Immutable
data class MobileSheetLine(
    val label: String,
    val icon: ImageVector?,
    val tag: String,
    /** A second line under the label (« environ 1,8 Go par heure », M2). */
    val detail: String? = null,
    val onClick: () -> Unit,
)

/**
 * A sheet from the bottom (`SPEC.md` § 4.23 C, M1): the menu of a card on a long press, a choice of
 * tracks or of a sort. Its title and subtitle on top, then its lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = CineTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(MobileSheetTags.SHEET),
        shape = RoundedCornerShape(topStart = MobileSizes.sheetCorner, topEnd = MobileSizes.sheetCorner),
        containerColor = colors.surface2,
        contentColor = colors.text,
        scrimColor = colors.dialogScrim,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = CineSpacing.s)) {
            Column(Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs)) {
                Text(title, style = CineTheme.typography.title2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = CineTheme.typography.caption, color = colors.text2, maxLines = 2) }
            }
            content()
        }
    }
}

/** The lines of a [MobileSheet], each 56 dp high. */
@Composable
fun MobileSheetLines(
    lines: List<MobileSheetLine>,
    selected: String? = null,
) {
    lines.forEach { line ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MobileSizes.touchTarget + CineSpacing.xs)
                    .clickable(role = Role.Button, onClick = line.onClick)
                    .testTag(line.tag)
                    .padding(horizontal = MobileSizes.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            line.icon?.let { Icon(it, contentDescription = null, tint = CineTheme.colors.text2) }
            Column(Modifier.weight(1f)) {
                Text(line.label, style = CineTheme.typography.body)
                line.detail?.let { Text(it, style = CineTheme.typography.caption, color = CineTheme.colors.text2) }
            }
            if (line.tag == selected) Icon(CineIcons.Watched, contentDescription = null, tint = CineTheme.colors.accent)
        }
    }
}

object MobileSheetTags {
    const val SHEET = "mobile-sheet"
}

/** A key of the PIN pad: a disc of 72 dp. */
@Composable
fun MobileKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Box(
        modifier =
            modifier
                .size(MobileSizes.keypadKey)
                .clip(CircleShape)
                .background(CineTheme.colors.surface2)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label)
        } else {
            Text(label, style = CineTheme.typography.title1)
        }
    }
}

/** The four dots of a PIN, filled as it is typed. */
@Composable
fun MobilePinDots(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(MobileSizes.pinDot)
                    .clip(CircleShape)
                    .background(if (index < filled) CineTheme.colors.accent else CineTheme.colors.surface3),
            )
        }
    }
}

/**
 * A screen of the sign-in path on the phone (M1): the name of the app, a title, a line, then the
 * form; it scrolls, keeps clear of the bars of the system and of the keyboard, and stays readable on a
 * tablet ([MobileSizes.formWidth]).
 */
@Composable
fun MobileFormScreen(
    brand: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CineTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding()
                .imePadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MobileSizes.formWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                Text(brand, style = CineTheme.typography.label, color = colors.accent)
                Text(title, style = CineTheme.typography.display, color = colors.text)
                subtitle?.let { Text(it, style = CineTheme.typography.body, color = colors.text2) }
                Box(Modifier.heightIn(min = CineSpacing.xs))
                content()
            }
        }
        onBack?.let { MobileIconButton(CineIcons.Back, backLabel, it, Modifier.align(Alignment.TopStart)) }
    }
}
