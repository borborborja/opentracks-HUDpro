package cat.hudpro.opentracks.data.map

/**
 * Catalogue of online base-map sources. ICGC serves standard EPSG:3857 tiles
 * (grid MON3857NW, zoom 1-20, PNG) with no API key, licensed CC-BY © ICGC.
 * See https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/1.0.0/WMTSCapabilities.xml
 */
enum class MapSource(
    val id: String,
    val displayName: String,
    val kind: Kind,
    /** Raster XYZ template ({z}/{x}/{y}) or vector style-JSON URL depending on [kind]. */
    val url: String,
    val attribution: String,
    val maxZoom: Int = 20,
) {
    OSM(
        id = "osm",
        displayName = "OpenStreetMap",
        kind = Kind.RASTER,
        url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = "© OpenStreetMap contributors",
        maxZoom = 19,
    ),
    ICGC_TOPO(
        id = "icgc_topografic",
        displayName = "ICGC Topogràfic",
        kind = Kind.RASTER,
        url = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/topografic/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya (ICGC)",
    ),
    ICGC_TOPO_GRIS(
        id = "icgc_topografic_gris",
        displayName = "ICGC Topogràfic gris",
        kind = Kind.RASTER,
        url = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/topografic-gris/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya (ICGC)",
    ),
    ICGC_ORTO(
        id = "icgc_orto",
        displayName = "ICGC Ortofoto",
        kind = Kind.RASTER,
        url = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/orto/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya (ICGC)",
    ),
    ICGC_ORTO_HIBRIDA(
        id = "icgc_orto_hibrida",
        displayName = "ICGC Ortofoto híbrida",
        kind = Kind.RASTER,
        url = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/orto-hibrida/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya (ICGC), © OpenStreetMap contributors",
    ),
    ICGC_GEOLOGIC(
        id = "icgc_geologic",
        displayName = "ICGC Geològic",
        kind = Kind.RASTER,
        url = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/geologic/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya (ICGC)",
    );

    enum class Kind { RASTER, VECTOR_STYLE }

    companion object {
        val DEFAULT = ICGC_TOPO
        fun byId(id: String?): MapSource = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
