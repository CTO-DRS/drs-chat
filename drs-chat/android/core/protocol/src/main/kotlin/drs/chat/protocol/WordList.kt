package drs.chat.protocol

object WordList {
    val LIST: List<String> =
        listOf(
            "acorn", "action", "actor", "adagio", "admiral", "agent", "alarm", "album",
            "alpine", "amber", "anchor", "anvil", "apple", "april", "arrow", "atlas",
            "audio", "azure", "bacon", "badge", "baker", "ballot", "banana", "banner",
            "barrel", "basil", "beacon", "beaver", "beluga", "birch", "blade", "blanket",
            "blazer", "blossom", "bobcat", "bonfire", "bonsai", "border", "bowtie", "bracket",
            "bronze", "brush", "bucket", "buffalo", "cabin", "cactus", "candle", "canyon",
            "caramel", "cargo", "castle", "cedar", "celery", "cello", "chair", "chalk",
            "charm", "cheetah", "cherry", "chimney", "cider", "circuit", "clarinet", "clover",
            "cobra", "cocoa", "comet", "compass", "copper", "coral", "cosine", "cotton",
            "cougar", "crane", "crater", "cricket", "crimson", "cursor", "curtain", "cycle",
            "dahlia", "daisy", "delta", "denim", "desert", "diamond", "digital", "diploma",
            "disco", "doctor", "dolphin", "donkey", "drizzle", "drum", "dryad", "duchess",
            "dugout", "dulcimer", "dune", "durian", "dwarf", "eagle", "earring", "echo",
            "edge", "editor", "egret", "elixir", "elk", "ember", "emerald", "engine",
            "ermine", "espresso", "etching", "everest", "fable", "falcon", "family", "fawn",
            "feather", "ferret", "fiesta", "fig", "finch", "firefly", "fjord", "flannel",
            "flask", "fleur", "flute", "foliage", "forklift", "fortune", "fossil", "fountain",
            "fox", "fragrant", "fringe", "frog", "galaxy", "gamut", "garage", "garnet",
            "gasket", "gateway", "gazelle", "gecko", "gentian", "ginkgo", "glacier", "glass",
            "glider", "gondola", "goose", "gorilla", "gourd", "granite", "graphite", "gravel",
            "griffin", "grizzly", "grotto", "guitar", "gulf", "gull", "guppy", "harbor",
            "harmony", "harp", "hawk", "hazel", "helix", "helmet", "heron", "hickory",
            "highway", "hilltop", "holly", "honey", "hornet", "horse", "hotel", "hound",
            "hummingbird", "hyena", "iceberg", "icon", "igloo", "indigo", "inkwell", "inlet",
            "insect", "island", "italic", "ivory", "jacket", "jade", "jaguar", "jasmine",
            "jasper", "jazz", "jelly", "jingle", "kayak", "kelp", "keystone", "kettle",
            "kiosk", "kite", "kiwi", "koala", "krypton", "ladder", "lagoon", "lapis",
            "laser", "lattice", "launch", "laurel", "lavender", "leaf", "lemon", "leopard",
            "library", "lighthouse", "lilac", "lily", "lion", "lizard", "llama", "loom",
            "loon", "lotion", "lullaby", "lumen", "lute", "lynx", "magnet", "magnolia",
            "mango", "manor", "maple", "marble", "marsh", "meadow", "melody", "melon",
            "mercury", "mesa", "meteor", "mica", "mink", "mint", "mitten", "monarch",
        )

    init {
        require(LIST.size == 256) { "word list must have 256 entries, got ${LIST.size}" }
        require(LIST.toSet().size == 256) { "word list must have 256 distinct entries" }
    }

    fun wordFor(byte: Byte): String = LIST[byte.toInt() and 0xFF]
}
