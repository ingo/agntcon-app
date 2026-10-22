package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.HideKeyboardOnDragHandler
import org.jetbrains.kotlinconf.ScrollToTopHandler
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.companies_error_no_data
import org.jetbrains.kotlinconf.generated.resources.companies_number_of_results
import org.jetbrains.kotlinconf.generated.resources.companies_showcase_filter
import org.jetbrains.kotlinconf.generated.resources.speakers_met_empty
import org.jetbrains.kotlinconf.generated.resources.speakers_tab_companies
import org.jetbrains.kotlinconf.generated.resources.speakers_tab_met
import org.jetbrains.kotlinconf.generated.resources.speakers_tab_speakers
import org.jetbrains.kotlinconf.generated.resources.speakers_error_no_data
import org.jetbrains.kotlinconf.generated.resources.speakers_number_of_results
import org.jetbrains.kotlinconf.generated.resources.speakers_title
import org.jetbrains.kotlinconf.ui.components.FilterTag
import org.jetbrains.kotlinconf.ui.components.HorizontalDivider
import org.jetbrains.kotlinconf.ui.components.Switcher
import org.jetbrains.kotlinconf.utils.ErrorLoadingContent
import org.jetbrains.kotlinconf.ui.components.MainHeaderContainer
import org.jetbrains.kotlinconf.ui.components.MainHeaderContainerState
import org.jetbrains.kotlinconf.ui.components.MainHeaderSearchBar
import org.jetbrains.kotlinconf.ui.components.MainHeaderTitleBar
import org.jetbrains.kotlinconf.ui.components.SpeakerCard
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.components.TopMenuButton
import org.jetbrains.kotlinconf.ui.generated.resources.UiRes
import org.jetbrains.kotlinconf.ui.generated.resources.main_header_search_hint
import org.jetbrains.kotlinconf.ui.generated.resources.search_24
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.bottomInsetPadding
import org.jetbrains.kotlinconf.utils.topInsetPadding

@Composable
fun SpeakersScreen(
    onSpeaker: (SpeakerId) -> Unit,
    onCompany: (String) -> Unit,
    viewModel: SpeakersViewModel = metroViewModel(),
) {
    var searchState by rememberSaveable { mutableStateOf(MainHeaderContainerState.Title) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var showcaseOnly by rememberSaveable { mutableStateOf(false) }

    val uiState = viewModel.speakers.collectAsStateWithLifecycle().value
    val hasCompanies by viewModel.hasCompanies.collectAsStateWithLifecycle()
    // Speakers, Companies (when speakers name theirs) and the people the attendee met
    val tabs = buildList {
        add(SpeakersTab.Speakers)
        if (hasCompanies) add(SpeakersTab.Companies)
        add(SpeakersTab.Met)
    }
    val tab = tabs.getOrElse(tabIndex) { SpeakersTab.Speakers }
    LaunchedEffect(showcaseOnly) { viewModel.setShowcaseOnly(showcaseOnly) }

    val gridState = rememberLazyGridState()

    LaunchedEffect(searchState, searchText) {
        if (searchState == MainHeaderContainerState.Search) {
            if (gridState.firstVisibleItemIndex > 1) {
                gridState.scrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
        viewModel.setSearchText(searchText)
    }

    Column(
        Modifier.fillMaxSize()
            .background(color = KotlinConfTheme.colors.mainBackground)
            .padding(topInsetPadding())
    ) {
        MainHeaderContainer(
            state = searchState,
            titleContent = {
                MainHeaderTitleBar(
                    title = stringResource(Res.string.speakers_title),
                    endContent = {
                        TopMenuButton(
                            icon = UiRes.drawable.search_24,
                            onClick = { searchState = MainHeaderContainerState.Search },
                            contentDescription = stringResource(UiRes.string.main_header_search_hint)
                        )
                    }
                )
            },
            searchContent = {
                NavigationBackHandler(
                    state = rememberNavigationEventState(NavigationEventInfo.None),
                    isBackEnabled = true,
                    onBackCompleted = {
                        searchState = MainHeaderContainerState.Title
                        searchText = ""
                    },
                )

                MainHeaderSearchBar(
                    searchValue = searchText,
                    onSearchValueChange = { searchText = it },
                    onClose = {
                        searchState = MainHeaderContainerState.Title
                        searchText = ""
                    },
                    onClear = { searchText = "" },
                )
            }
        )

        HorizontalDivider(1.dp, KotlinConfTheme.colors.strokePale)

        if (tabs.size > 1) {
            val titles = tabs.map { stringResource(it.title) }
            Switcher(
                items = titles,
                shortItems = null,
                selectedIndex = tabs.indexOf(tab),
                onSelect = { tabIndex = it },
                scrollWhenCramped = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        when (tab) {
            SpeakersTab.Companies -> CompaniesList(
                viewModel = viewModel,
                showcaseOnly = showcaseOnly,
                onShowcaseOnly = { showcaseOnly = it },
                isSearch = searchState == MainHeaderContainerState.Search,
                onCompany = onCompany,
            )
            SpeakersTab.Met -> MetList(viewModel, onSpeaker)
            SpeakersTab.Speakers -> ErrorLoadingContent(
            state = uiState,
            errorMessage = stringResource(Res.string.speakers_error_no_data),
            onRetry = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) { speakers ->
            ScrollToTopHandler(gridState)
            HideKeyboardOnDragHandler(gridState)

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = bottomInsetPadding(),
            ) {
                if (searchState == MainHeaderContainerState.Search) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "number-of-speakers") {
                        Text(
                            text = pluralStringResource(
                                Res.plurals.speakers_number_of_results,
                                speakers.size,
                                speakers.size
                            ),
                            color = KotlinConfTheme.colors.secondaryText,
                            style = KotlinConfTheme.typography.text2,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp, bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
                gridItems(speakers, key = { it.speaker.id.id }) { speakerWithHighlights ->
                    val speaker = speakerWithHighlights.speaker
                    SpeakerCard(
                        name = speaker.name,
                        nameHighlights = speakerWithHighlights.nameHighlights,
                        title = speaker.position,
                        titleHighlights = speakerWithHighlights.titleHighlights,
                        photoUrl = speaker.photoUrl,
                        compact = true,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        onClick = { onSpeaker(speaker.id) },
                    )
                }
            }
        }
        }
    }
}

private enum class SpeakersTab(val title: StringResource) {
    Speakers(Res.string.speakers_tab_speakers),
    Companies(Res.string.speakers_tab_companies),
    Met(Res.string.speakers_tab_met),
}

@Composable
private fun CompaniesList(
    viewModel: SpeakersViewModel,
    showcaseOnly: Boolean,
    onShowcaseOnly: (Boolean) -> Unit,
    isSearch: Boolean,
    onCompany: (String) -> Unit,
) {
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopHandler(listState)
    HideKeyboardOnDragHandler(listState)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
        if (ActiveEvent.showcaseRoom != null) {
            item(key = "showcase-filter") {
                FilterTag(
                    label = stringResource(Res.string.companies_showcase_filter),
                    selected = showcaseOnly,
                    onSelect = onShowcaseOnly,
                    isExclusive = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        if (isSearch || showcaseOnly) {
            item(key = "number-of-companies") {
                Text(
                    text = pluralStringResource(Res.plurals.companies_number_of_results, companies.size, companies.size),
                    color = KotlinConfTheme.colors.secondaryText,
                    style = KotlinConfTheme.typography.text2,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp, bottom = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        if (companies.isEmpty() && !isSearch && !showcaseOnly) {
            item(key = "no-companies") {
                Text(
                    stringResource(Res.string.companies_error_no_data),
                    color = KotlinConfTheme.colors.secondaryText,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(companies, key = { it.company.name.lowercase() }) { item ->
            CompanyCard(
                company = item.company,
                nameHighlights = item.nameHighlights,
                showcaseOnly = showcaseOnly,
                onClick = { onCompany(item.company.name) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MetList(viewModel: SpeakersViewModel, onSpeaker: (SpeakerId) -> Unit) {
    val people by viewModel.metPeople.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopHandler(listState)
    HideKeyboardOnDragHandler(listState)
    val uriHandler = LocalUriHandler.current

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
        if (people.isEmpty()) {
            item(key = "no-met") {
                Text(
                    stringResource(Res.string.speakers_met_empty),
                    color = KotlinConfTheme.colors.secondaryText,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(people, key = { it.speaker.id.id }) { person ->
            MetPersonCard(
                person = person,
                onClick = { onSpeaker(person.speaker.id) },
                onOpenLink = uriHandler::openUri,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
