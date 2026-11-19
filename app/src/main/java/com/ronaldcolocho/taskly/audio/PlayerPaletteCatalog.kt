package com.ronaldcolocho.taskly.audio

// Stable palette IDs; original colors do not depend on playback order.
val PLAYER_PALETTES = listOf(
    // 1. Cian Eléctrico
    PlayerPalette(
        id        = "electric_cyan",
        name      = "Cian Eléctrico",
        primary   = 0x00F5FF,
        secondary = 0x0052D4,
        tertiary  = 0x4364F7,
        glow      = 0x80FAFF
    ),
    // 2. Glaciar Ártico
    PlayerPalette(
        id        = "nordic_glacier",
        name      = "Glaciar Ártico",
        primary   = 0x00D2FF,
        secondary = 0x0A369D,
        tertiary  = 0x3A86FF,
        glow      = 0x7FE5FF
    ),
    // 3. Marea Caribeña
    PlayerPalette(
        id        = "caribbean_tide",
        name      = "Marea Caribeña",
        primary   = 0x00F0B5,
        secondary = 0x028090,
        tertiary  = 0x00A896,
        glow      = 0x8AFFE0
    ),
    // 4. Aqua Cristalino
    PlayerPalette(
        id        = "crystal_aqua",
        name      = "Aqua Cristalino",
        primary   = 0x2DE2E6,
        secondary = 0x0353A4,
        tertiary  = 0x0466C8,
        glow      = 0x9CF8FA
    ),
    // 5. Abismo Marino
    PlayerPalette(
        id        = "deep_abyss",
        name      = "Abismo Marino",
        primary   = 0x00B4D8,
        secondary = 0x03045E,
        tertiary  = 0x0077B6,
        glow      = 0x90E0EF
    ),
    // 6. Aurora Menta
    PlayerPalette(
        id        = "mint_aurora",
        name      = "Aurora Menta",
        primary   = 0x05FFA1,
        secondary = 0x008F7A,
        tertiary  = 0x00C9A7,
        glow      = 0x80FFD0
    ),
    // 7. Hielo Polar
    PlayerPalette(
        id        = "polar_ice",
        name      = "Hielo Polar",
        primary   = 0x7FC8D9,
        secondary = 0x1A3840,
        tertiary  = 0xA2DFEC,
        glow      = 0xC8F0F7
    ),
    // 8. Cobalto Real
    PlayerPalette(
        id        = "royal_cobalt",
        name      = "Cobalto Real",
        primary   = 0x2563EB,
        secondary = 0x1E3A8A,
        tertiary  = 0x60A5FA,
        glow      = 0x93C5FD
    ),
    // 9. Azul Ultraneón
    PlayerPalette(
        id        = "electric_blue",
        name      = "Azul Ultraneón",
        primary   = 0x1D4ED8,
        secondary = 0x4F46E5,
        tertiary  = 0x818CF8,
        glow      = 0x67E8F9
    ),
    // 10. Océano Profundo
    PlayerPalette(
        id        = "deep_ocean",
        name      = "Océano Profundo",
        primary   = 0x2F7FBF,
        secondary = 0x0A2840,
        tertiary  = 0x58A6E4,
        glow      = 0x8AC8F8
    ),
    // 11. Cielo Ultramar
    PlayerPalette(
        id        = "ultramarine_sky",
        name      = "Cielo Ultramar",
        primary   = 0x3B82F6,
        secondary = 0x1D2D50,
        tertiary  = 0x93C5FD,
        glow      = 0xBFDBFE
    ),
    // 12. Ola de Medianoche
    PlayerPalette(
        id        = "midnight_wave",
        name      = "Ola de Medianoche",
        primary   = 0x1E40AF,
        secondary = 0x0B132B,
        tertiary  = 0x4895EF,
        glow      = 0x60A5FA
    ),
    // 13. Pulso Índigo
    PlayerPalette(
        id        = "indigo_pulse",
        name      = "Pulso Índigo",
        primary   = 0x4338CA,
        secondary = 0x1E1B4B,
        tertiary  = 0x818CF8,
        glow      = 0xA5B4FC
    ),
    // 14. Azul Cuántico
    PlayerPalette(
        id        = "quantum_blue",
        name      = "Azul Cuántico",
        primary   = 0x312E81,
        secondary = 0x03071E,
        tertiary  = 0x4361EE,
        glow      = 0x6366F1
    ),
    // 15. Brisa Egea
    PlayerPalette(
        id        = "aegean_breeze",
        name      = "Brisa Egea",
        primary   = 0x0284C7,
        secondary = 0x075985,
        tertiary  = 0x7DD3FC,
        glow      = 0xBAE6FD
    ),
    // 16. Amatista Mística
    PlayerPalette(
        id        = "mystic_amethyst",
        name      = "Amatista Mística",
        primary   = 0x8B5CF6,
        secondary = 0x3B0764,
        tertiary  = 0xC4B5FD,
        glow      = 0xDDD6FE
    ),
    // 17. Aurora Violeta
    PlayerPalette(
        id        = "violet_aurora",
        name      = "Aurora Violeta",
        primary   = 0x8E6CD1,
        secondary = 0x2C1A4D,
        tertiary  = 0xB295EB,
        glow      = 0xD4C2F7
    ),
    // 18. Lavanda Astral
    PlayerPalette(
        id        = "astral_lavender",
        name      = "Lavanda Astral",
        primary   = 0xA855F7,
        secondary = 0x581C87,
        tertiary  = 0xD8B4FE,
        glow      = 0xF3E8FF
    ),
    // 19. Púrpura Galáctico
    PlayerPalette(
        id        = "deep_purple",
        name      = "Púrpura Galáctico",
        primary   = 0x9333EA,
        secondary = 0x2E1065,
        tertiary  = 0xC084FC,
        glow      = 0xE9D5FF
    ),
    // 20. Iris Cibernético
    PlayerPalette(
        id        = "cyber_iris",
        name      = "Iris Cibernético",
        primary   = 0x6D28D9,
        secondary = 0x1E1B4B,
        tertiary  = 0x8B5CF6,
        glow      = 0xA78BFA
    ),
    // 21. Noche Aterciopelada
    PlayerPalette(
        id        = "velvet_night",
        name      = "Noche Aterciopelada",
        primary   = 0x581C87,
        secondary = 0x170F28,
        tertiary  = 0x9333EA,
        glow      = 0xC084FC
    ),
    // 22. Lila Luminoso
    PlayerPalette(
        id        = "neon_lilac",
        name      = "Lila Luminoso",
        primary   = 0xB185DB,
        secondary = 0x4A154B,
        tertiary  = 0xDDA0DD,
        glow      = 0xE8D7F1
    ),
    // 23. Cíber Fucsia
    PlayerPalette(
        id        = "cyber_magenta",
        name      = "Cíber Fucsia",
        primary   = 0xFF007F,
        secondary = 0x7928CA,
        tertiary  = 0xFF4D94,
        glow      = 0xFF80BF
    ),
    // 24. Orquídea Eléctrica
    PlayerPalette(
        id        = "electric_orchid",
        name      = "Orquídea Eléctrica",
        primary   = 0xD946EF,
        secondary = 0x701A75,
        tertiary  = 0xF472B6,
        glow      = 0xFBCFE8
    ),
    // 25. Flor de Sakura
    PlayerPalette(
        id        = "sakura_bloom",
        name      = "Flor de Sakura",
        primary   = 0xEC4899,
        secondary = 0x831843,
        tertiary  = 0xFBCFE8,
        glow      = 0xFCE7F3
    ),
    // 26. Chicle Neón
    PlayerPalette(
        id        = "neon_bubblegum",
        name      = "Chicle Neón",
        primary   = 0xF43F5E,
        secondary = 0x881337,
        tertiary  = 0xFB7185,
        glow      = 0xFECDD3
    ),
    // 27. Rosa Hiperneón
    PlayerPalette(
        id        = "hyper_pink",
        name      = "Rosa Hiperneón",
        primary   = 0xFF1493,
        secondary = 0x4A0033,
        tertiary  = 0xFF69B4,
        glow      = 0xFF85C0
    ),
    // 28. Nebulosa Magenta
    PlayerPalette(
        id        = "magenta_nebula",
        name      = "Nebulosa Magenta",
        primary   = 0xBE185D,
        secondary = 0x3B0764,
        tertiary  = 0xF472B6,
        glow      = 0xF9A8D4
    ),
    // 29. Cuarzo Rosa
    PlayerPalette(
        id        = "rose_quartz",
        name      = "Cuarzo Rosa",
        primary   = 0xFB7185,
        secondary = 0x4C0519,
        tertiary  = 0xFDA4AF,
        glow      = 0xFFE4E6
    ),
    // 30. Coral Vivo
    PlayerPalette(
        id        = "living_coral",
        name      = "Coral Vivo",
        primary   = 0xE4616B,
        secondary = 0x4A1218,
        tertiary  = 0xF58C94,
        glow      = 0xFBBEC2
    ),
    // 31. Rubí Sangre
    PlayerPalette(
        id        = "blood_ruby",
        name      = "Rubí Sangre",
        primary   = 0xFF1E56,
        secondary = 0x8B0000,
        tertiary  = 0xFF647C,
        glow      = 0xFFA3B2
    ),
    // 32. Carmesí Tokio
    PlayerPalette(
        id        = "tokyo_crimson",
        name      = "Carmesí Tokio",
        primary   = 0xE11D48,
        secondary = 0x4C0519,
        tertiary  = 0xFB7185,
        glow      = 0xFDA4AF
    ),
    // 33. Lava Volcánica
    PlayerPalette(
        id        = "volcanic_lava",
        name      = "Lava Volcánica",
        primary   = 0xDC2626,
        secondary = 0x450A0A,
        tertiary  = 0xF87171,
        glow      = 0xFCA5A5
    ),
    // 34. Fuego Escarlata
    PlayerPalette(
        id        = "scarlet_fire",
        name      = "Fuego Escarlata",
        primary   = 0xEF4444,
        secondary = 0x7F1D1D,
        tertiary  = 0xFCA5A5,
        glow      = 0xFECACA
    ),
    // 35. Granate Real
    PlayerPalette(
        id        = "dark_garnet",
        name      = "Granate Real",
        primary   = 0x881337,
        secondary = 0x38040E,
        tertiary  = 0xBE123C,
        glow      = 0xFB7185
    ),
    // 36. Cereza Negra
    PlayerPalette(
        id        = "black_cherry",
        name      = "Cereza Negra",
        primary   = 0x9F1239,
        secondary = 0x2A0813,
        tertiary  = 0xE11D48,
        glow      = 0xFDA4AF
    ),
    // 37. Carmesí Imperial
    PlayerPalette(
        id        = "imperial_crimson",
        name      = "Carmesí Imperial",
        primary   = 0xBE123C,
        secondary = 0x4C0519,
        tertiary  = 0xFB7185,
        glow      = 0xFECDD3
    ),
    // 38. Llama Atómica
    PlayerPalette(
        id        = "atomic_flame",
        name      = "Llama Atómica",
        primary   = 0xFF3366,
        secondary = 0x660022,
        tertiary  = 0xFF6688,
        glow      = 0xFFA3B8
    ),
    // 39. Atardecer Carmesí
    PlayerPalette(
        id        = "sunset_crimson",
        name      = "Atardecer Carmesí",
        primary   = 0xFF2A6D,
        secondary = 0xFF5E36,
        tertiary  = 0xFFA726,
        glow      = 0xFF8AA5
    ),
    // 40. Cobre Ígneo
    PlayerPalette(
        id        = "fiery_copper",
        name      = "Cobre Ígneo",
        primary   = 0xF97316,
        secondary = 0x7C2D12,
        tertiary  = 0xFDBA74,
        glow      = 0xFFEDD5
    ),
    // 41. Llamarada Solar
    PlayerPalette(
        id        = "solar_flare",
        name      = "Llamarada Solar",
        primary   = 0xFF6B35,
        secondary = 0x8B1E00,
        tertiary  = 0xFF9F1C,
        glow      = 0xFFB997
    ),
    // 42. Especias de Bengala
    PlayerPalette(
        id        = "bengal_spice",
        name      = "Especias de Bengala",
        primary   = 0xEA580C,
        secondary = 0x431407,
        tertiary  = 0xFB923C,
        glow      = 0xFED7AA
    ),
    // 43. Mandarina Crepúsculo
    PlayerPalette(
        id        = "twilight_tangerine",
        name      = "Mandarina Crepúsculo",
        primary   = 0xFB923C,
        secondary = 0x7C2D12,
        tertiary  = 0xFDE047,
        glow      = 0xFEF08A
    ),
    // 44. Ámbar Volcánico
    PlayerPalette(
        id        = "volcanic_amber",
        name      = "Ámbar Volcánico",
        primary   = 0xC2410C,
        secondary = 0x3A1000,
        tertiary  = 0xEA580C,
        glow      = 0xFDBA74
    ),
    // 45. Durazno Tropical
    PlayerPalette(
        id        = "tropical_peach",
        name      = "Durazno Tropical",
        primary   = 0xFF6B6B,
        secondary = 0x802020,
        tertiary  = 0xFF8E53,
        glow      = 0xFFB4B4
    ),
    // 46. Terracota Zen
    PlayerPalette(
        id        = "terracotta_zen",
        name      = "Terracota Zen",
        primary   = 0xD9534F,
        secondary = 0x4A1515,
        tertiary  = 0xF0AD4E,
        glow      = 0xF8D7DA
    ),
    // 47. Oro Solar
    PlayerPalette(
        id        = "solar_gold",
        name      = "Oro Solar",
        primary   = 0xEAB308,
        secondary = 0x713F12,
        tertiary  = 0xFDE047,
        glow      = 0xFEF9C3
    ),
    // 48. Oro Celestial
    PlayerPalette(
        id        = "celestial_gold",
        name      = "Oro Celestial",
        primary   = 0xFACC15,
        secondary = 0x854D0E,
        tertiary  = 0xFEF08A,
        glow      = 0xFEFCE8
    ),
    // 49. Duna Dorada
    PlayerPalette(
        id        = "golden_dune",
        name      = "Duna Dorada",
        primary   = 0xF28C28,
        secondary = 0x451A03,
        tertiary  = 0xFCD34D,
        glow      = 0xFDE68A
    ),
    // 50. Ámbar Silvestre
    PlayerPalette(
        id        = "wild_amber",
        name      = "Ámbar Silvestre",
        primary   = 0xB45309,
        secondary = 0x2E1202,
        tertiary  = 0xF59E0B,
        glow      = 0xFDE68A
    ),
    // 51. Miel Pura
    PlayerPalette(
        id        = "honeycomb_glow",
        name      = "Miel Pura",
        primary   = 0xF59E0B,
        secondary = 0x78350F,
        tertiary  = 0xFBBF24,
        glow      = 0xFEF3C7
    ),
    // 52. Citrino Neón
    PlayerPalette(
        id        = "lemon_zest",
        name      = "Citrino Neón",
        primary   = 0xFFE600,
        secondary = 0x665C00,
        tertiary  = 0xFFF066,
        glow      = 0xFFFF99
    ),
    // 53. Neón Lima
    PlayerPalette(
        id        = "neon_lime",
        name      = "Neón Lima",
        primary   = 0xCCFF00,
        secondary = 0x15803D,
        tertiary  = 0x84CC16,
        glow      = 0xE6FF80
    ),
    // 54. Verde Eléctrico
    PlayerPalette(
        id        = "acid_green",
        name      = "Verde Eléctrico",
        primary   = 0xA3E635,
        secondary = 0x365314,
        tertiary  = 0xBEF264,
        glow      = 0xECFCCB
    ),
    // 55. Té Matcha
    PlayerPalette(
        id        = "zen_matcha",
        name      = "Té Matcha",
        primary   = 0x65A30D,
        secondary = 0x1A2E05,
        tertiary  = 0x84CC16,
        glow      = 0xBEF264
    ),
    // 56. Chartreuse Vivo
    PlayerPalette(
        id        = "chartreuse_glow",
        name      = "Chartreuse Vivo",
        primary   = 0x84CC16,
        secondary = 0x1E3A0A,
        tertiary  = 0xA3E635,
        glow      = 0xD9F99D
    ),
    // 57. Aurora Boreal
    PlayerPalette(
        id        = "nordic_aurora",
        name      = "Aurora Boreal",
        primary   = 0x00F260,
        secondary = 0x0575E6,
        tertiary  = 0x00F5D4,
        glow      = 0x80FFB0
    ),
    // 58. Bosque Noche
    PlayerPalette(
        id        = "night_forest",
        name      = "Bosque Noche",
        primary   = 0x3E9B6E,
        secondary = 0x0F3320,
        tertiary  = 0x62BE90,
        glow      = 0x93DCB6
    ),
    // 59. Esmeralda Sublime
    PlayerPalette(
        id        = "sublime_emerald",
        name      = "Esmeralda Sublime",
        primary   = 0x059669,
        secondary = 0x022C22,
        tertiary  = 0x6EE7B7,
        glow      = 0xA7F3D0
    ),
    // 60. Selva Tropical
    PlayerPalette(
        id        = "rainforest_bamboo",
        name      = "Selva Tropical",
        primary   = 0x16A34A,
        secondary = 0x052E16,
        tertiary  = 0x4ADE80,
        glow      = 0xBBF7D0
    )
)

data class PlayerPresetColor(
    val id: String,
    val name: String,
    val colorHex: Long
)

val PRESET_PLAYER_COLORS = listOf(
    PlayerPresetColor("golden_dune", "Duna Dorada", 0xFFF28C28L),
    PlayerPresetColor("deep_ocean", "Océano Profundo", 0xFF2F7FBFL),
    PlayerPresetColor("night_forest", "Bosque Noche", 0xFF3E9B6EL),
    PlayerPresetColor("violet_aurora", "Aurora Violeta", 0xFF8E6CD1L),
    PlayerPresetColor("living_coral", "Coral Vivo", 0xFFE4616BL),
    PlayerPresetColor("polar_ice", "Hielo Polar", 0xFF7FC8D9L)
)
