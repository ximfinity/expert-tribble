package com.phantomfloor.scenes

/**
 * Groups scenes in the control panel. [icon] and [color] (ARGB) style the category tabs so they
 * look nothing like the animation buttons below them.
 */
enum class Season(val label: String, val icon: String, val color: Int) {
    HALLOWEEN("Halloween", "🎃", 0xFFFF8A1F.toInt()),
    SCARY("Scary", "💀", 0xFFE0323C.toInt()),
    WINTER("Winter", "❄️", 0xFF6EC6FF.toInt()),
    FALL("Fall", "🍂", 0xFFD9822B.toInt()),
    SPRING("Spring", "🌷", 0xFFFF7EB6.toInt()),
    JULY_4TH("4th of July", "🎆", 0xFF5B8CFF.toInt()),
    SUMMER("Summer", "☀️", 0xFFFFD23F.toInt()),
    GAMES("Games", "🎮", 0xFF7CFF9B.toInt()),
}
