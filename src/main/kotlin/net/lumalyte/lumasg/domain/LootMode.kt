package net.lumalyte.lumasg.domain

enum class LootMode {
    CLASSIC, MODERN, OP;

    companion object {
        /** Parse a mode name (case-insensitive), returning null for invalid input. */
        fun fromString(name: String): LootMode? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
