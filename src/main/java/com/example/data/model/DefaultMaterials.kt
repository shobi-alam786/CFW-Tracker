package com.example.data.model

data class PredefinedMaterial(
    val name: String,
    val category: String,
    val defaultUnit: String,
    val iconName: String = "build"
)

object DefaultMaterials {
    val UNITS = listOf(
        "Pieces",
        "Bag",
        "KG",
        "Meter",
        "Feet",
        "Bundle",
        "Roll",
        "Number",
        "Custom"
    )

    val LIST = listOf(
        PredefinedMaterial("Cement", "Masonry", "Bag"),
        PredefinedMaterial("Brick", "Masonry", "Pieces"),
        PredefinedMaterial("Brick Chips", "Masonry", "Bag"),
        PredefinedMaterial("Sand", "Masonry", "Feet"),
        PredefinedMaterial("8mm Rebar", "Steel", "KG"),
        PredefinedMaterial("10mm Rebar", "Steel", "KG"),
        PredefinedMaterial("12mm Rebar", "Steel", "KG"),
        PredefinedMaterial("Borak Bamboo", "Bamboo", "Pieces"),
        PredefinedMaterial("Muli Bamboo", "Bamboo", "Pieces"),
        PredefinedMaterial("3mm Rope", "Binding", "Roll"),
        PredefinedMaterial("6mm Rope", "Binding", "Roll"),
        PredefinedMaterial("Geo Roll", "Geo Synthetic", "Roll"),
        PredefinedMaterial("Geo Bag", "Geo Synthetic", "Pieces"),
        PredefinedMaterial("Tarpaulin", "Covering", "Pieces"),
        PredefinedMaterial("Black Plastic", "Covering", "Meter"),
        PredefinedMaterial("GI Wire", "Hardware", "KG"),
        PredefinedMaterial("Nail (2.5 inch)", "Hardware", "KG"),
        PredefinedMaterial("Nail (2 inch)", "Hardware", "KG"),
        PredefinedMaterial("Permeable Block", "Concrete", "Pieces"),
        PredefinedMaterial("Plywood", "Timber", "Pieces"),
        PredefinedMaterial("Wood Beam", "Timber", "Pieces"),
        PredefinedMaterial("Precast Beam 5×4", "Concrete", "Pieces"),
        PredefinedMaterial("Precast Beam 5×5", "Concrete", "Pieces"),
        PredefinedMaterial("Buttress Columns", "Concrete", "Pieces")
    )
}
