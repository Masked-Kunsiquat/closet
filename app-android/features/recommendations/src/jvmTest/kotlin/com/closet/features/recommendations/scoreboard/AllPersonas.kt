package com.closet.features.recommendations.scoreboard

import com.closet.features.recommendations.scoreboard.personas.ActiveStudentPersona
import com.closet.features.recommendations.scoreboard.personas.CreatureOfHabitMenswearPersona
import com.closet.features.recommendations.scoreboard.personas.MixAndMatcherWomenswearPersona
import com.closet.features.recommendations.scoreboard.personas.OfficeCommuterMenswearPersona
import com.closet.features.recommendations.scoreboard.personas.OfficeCommuterWomenswearPersona

/** The five curated scoreboard personas (#148). */
object AllPersonas {
    val list: List<Persona> = listOf(
        OfficeCommuterMenswearPersona.build(),
        OfficeCommuterWomenswearPersona.build(),
        CreatureOfHabitMenswearPersona.build(),
        MixAndMatcherWomenswearPersona.build(),
        ActiveStudentPersona.build(),
    )
}
