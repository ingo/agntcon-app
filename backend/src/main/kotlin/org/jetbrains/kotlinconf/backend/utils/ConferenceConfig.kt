package org.jetbrains.kotlinconf.backend.utils

import io.ktor.server.config.ApplicationConfig
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.sessionize.SessionizeRules

class ConferenceConfig(config: ApplicationConfig) {
    val imagesUrl: String = config.property("sessionize.imagesUrl").getString()
    val sessionizeUrl: String = config.property("sessionize.url").getString()
    val sessionizeInterval = config.property("sessionize.interval").getString().toLong()
    val sessionizeRules = SessionizeRules(
        excludedTags = config.propertyOrNull("sessionize.excludedTags")?.getList()?.toSet() ?: emptySet(),
        multipartWorkshopTag = config.propertyOrNull("sessionize.multipartWorkshopTag")?.getString()
            ?.takeIf { it.isNotBlank() },
        roomSuffixesToStrip = config.propertyOrNull("sessionize.roomSuffixesToStrip")?.getList() ?: emptyList(),
        slidesQuestion = config.propertyOrNull("sessionize.slidesQuestion")?.getString()?.takeIf { it.isNotBlank() },
    )

    val adminSecret: String = config.property("service.secret").getString()

    val currentYear: Int = config.property("conference.currentYear").getString().toInt()
    val supportedYears: List<Int> = config.property("conference.supportedYears")
        .getList()
        .map { it.toInt() }
    val baseUrl: String = config.property("conference.baseUrl").getString().trimEnd('/')
    val timeZone: TimeZone = TimeZone.of(config.property("conference.timeZone").getString())
}
