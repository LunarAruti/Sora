package ucadmin.tools.pixelgenerator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Package-local token normalization and palette support.
 *
 * <p>This keeps token rules centralized so the renderer and any grid-building
 * helpers resolve tokens the same way. The class is intentionally package-
 * private because it is an implementation detail, not part of the public API.</p>
 */
final class PixelTokenSupport {

    /**
     * Canonical token used for fully transparent cells.
     */
    static final String TRANSPARENT_TOKEN = ".";

    /**
     * Default hard-coded palette in normalized token form.
     *
     * <p>This map is intentionally large. The renderer stays simple by keeping
     * palette complexity here instead of exposing more API surface area. Art
     * can therefore use short tokens for common colors and descriptive names
     * for niche colors without changing renderer behavior.</p>
     */
    static final Map<String, Integer> DEFAULT_PALETTE = buildPalette();

    private PixelTokenSupport() {}

    /**
     * Normalizes a raw token into the canonical lookup/storage form.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>null -> transparent</li>
     *     <li>trim surrounding whitespace</li>
     *     <li>lowercase using {@link Locale#ROOT}</li>
     *     <li>blank after trim -> transparent</li>
     * </ul>
     *
     * @param rawToken raw source token
     * @return normalized token
     */
    static String normalize(String rawToken) {
        if (rawToken == null) {
            return TRANSPARENT_TOKEN;
        }

        String normalized = rawToken.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? TRANSPARENT_TOKEN : normalized;
    }

    /**
     * Returns true when the normalized token is the transparent token.
     *
     * @param token normalized token
     * @return true if transparent
     */
    static boolean isTransparent(String token) {
        return TRANSPARENT_TOKEN.equals(token);
    }

    /**
     * Resolves a source token into a packed ARGB color using the default palette.
     *
     * <p>Null tokens, empty strings, and the explicit transparent token all
     * normalize into the same fully transparent output value. Any other token
     * must exist in the fixed palette or the renderer will throw.</p>
     *
     * @param rawToken raw grid token
     * @param x source column index
     * @param y source row index
     * @return packed ARGB color
     * @throws PixelGeneratorException if the token is unknown
     */
    static int resolveColor(String rawToken, int x, int y) {
        String token = normalize(rawToken);
        Integer argb = DEFAULT_PALETTE.get(token);
        if (argb == null) {
            throw new PixelGeneratorException(
                    "PixelTokenSupport.resolveColor: unknown token '" + token + "' at x=" + x + ", y=" + y + "."
            );
        }
        return argb;
    }

    /**
     * Resolves a non-grid token into a packed ARGB color.
     *
     * <p>This is used for auxiliary render settings such as borders where the
     * failure should mention a named context rather than a grid coordinate.</p>
     *
     * @param rawToken raw token to resolve
     * @param context human-readable context label used in failure messages
     * @return packed ARGB color
     * @throws PixelGeneratorException if the token is unknown
     */
    static int resolveNamedColor(String rawToken, String context) {
        String token = normalize(rawToken);
        Integer argb = DEFAULT_PALETTE.get(token);
        if (argb == null) {
            throw new PixelGeneratorException(
                    "PixelTokenSupport.resolveNamedColor: unknown token '" + token + "' for " + context + "."
            );
        }
        return argb;
    }

    /**
     * Builds the fixed palette used by the renderer.
     *
     * <p>The palette intentionally includes both short artist-friendly aliases
     * and longer descriptive color names. This keeps the runtime API small
     * while still giving source art a large token vocabulary.</p>
     *
     * @return unmodifiable normalized token palette
     */
    private static Map<String, Integer> buildPalette() {
        Map<String, Integer> palette = new LinkedHashMap<>();

        // Transparency aliases.
        addColor(palette, 0x00000000, ".", "t", "tr", "transparent", "clear", "none", "empty", "blank");

        // Core grayscale.
        addColor(palette, 0xFF000000, "b", "bk", "black");
        addColor(palette, 0xFF0A0A0A, "jet");
        addColor(palette, 0xFF0B0B0B, "richblack");
        addColor(palette, 0xFF050505, "inkblack");
        addColor(palette, 0xFF111111, "offblack", "nearblack");
        addColor(palette, 0xFF1C1C1C, "soot");
        addColor(palette, 0xFF2B2B2B, "coal");
        addColor(palette, 0xFF2F2F2F, "charcoal");
        addColor(palette, 0xFF353839, "onyx");
        addColor(palette, 0xFF3A3A3A, "carbon");
        addColor(palette, 0xFF2A3439, "gunmetal");
        addColor(palette, 0xFF2F4F4F, "darkslategray", "darkslategrey", "dsg");
        addColor(palette, 0xFF696969, "dimgray", "dimgrey", "dmg", "darkgray1");
        addColor(palette, 0xFF4A4A4A, "darkcharcoal");
        addColor(palette, 0xFF555555, "graphite");
        addColor(palette, 0xFF708090, "slategray", "slategrey");
        addColor(palette, 0xFF778899, "lightslategray", "lightslategrey", "lsg");
        addColor(palette, 0xFF808080, "s", "gy", "gray", "grey");
        addColor(palette, 0xFF7D8597, "coolgray");
        addColor(palette, 0xFF8B7E74, "warmgray");
        addColor(palette, 0xFF6D7B8D, "bluegray");
        addColor(palette, 0xFF928E85, "stonegray");
        addColor(palette, 0xFFB2BEB5, "ashgray");
        addColor(palette, 0xFF9EA7AA, "smokegray");
        addColor(palette, 0xFFD6DCE5, "cloudgray");
        addColor(palette, 0xFFA9A9A9, "darkgray", "darkgrey", "dgy");
        addColor(palette, 0xFFB0B0B0, "mediumgray", "mediumgrey", "mgy");
        addColor(palette, 0xFFC0C0C0, "silver");
        addColor(palette, 0xFFD3D3D3, "lightgray", "lightgrey", "lgrey", "lgray");
        addColor(palette, 0xFFE5E4E2, "platinum");
        addColor(palette, 0xFFD0D5DB, "tin");
        addColor(palette, 0xFF99A8A8, "pewter");
        addColor(palette, 0xFFDCDCDC, "gainsboro");
        addColor(palette, 0xFFF5F5F5, "whitesmoke");
        addColor(palette, 0xFFE3EAF0, "frostgray");
        addColor(palette, 0xFFEAEDEF, "porcelaingray");
        addColor(palette, 0xFFFFFFFF, "w", "wh", "white");

        // Warm whites / paper-like neutrals.
        addColor(palette, 0xFFFFFAFA, "snow");
        addColor(palette, 0xFFF8F8FF, "ghostwhite");
        addColor(palette, 0xFFF5F5DC, "beige");
        addColor(palette, 0xFFFDF5E6, "oldlace");
        addColor(palette, 0xFFF5F5F0, "eggshell");
        addColor(palette, 0xFFFAF0E6, "linen");
        addColor(palette, 0xFFFFF5EE, "seashell");
        addColor(palette, 0xFFFFFAF0, "floralwhite");
        addColor(palette, 0xFFFAEBD7, "antiquewhite");
        addColor(palette, 0xFFFFF8DC, "cornsilk");
        addColor(palette, 0xFFFFEFD5, "papayawhip");
        addColor(palette, 0xFFFFEBCD, "blanchedalmond");
        addColor(palette, 0xFFFFE4C4, "bisque");
        addColor(palette, 0xFFFFDEAD, "navajowhite");
        addColor(palette, 0xFFFFDAB9, "peachpuff");
        addColor(palette, 0xFFFFE4B5, "moccasin");
        addColor(palette, 0xFFFFFDD0, "cream");
        addColor(palette, 0xFFFFFFF0, "ivory");
        addColor(palette, 0xFFF3E5AB, "vanilla");
        addColor(palette, 0xFFFFF1B5, "buttermilk");
        addColor(palette, 0xFFEFDECD, "almond");
        addColor(palette, 0xFFF1E9D2, "parchment");
        addColor(palette, 0xFFDFD3B8, "oat");
        addColor(palette, 0xFFD9C7A1, "oatmeal");
        addColor(palette, 0xFFE3DAC9, "bone");
        addColor(palette, 0xFFFDFDFD, "chalk");
        addColor(palette, 0xFFF5E7D3, "sandstonewhite");
        addColor(palette, 0xFFFFF2C2, "buttercream");
        addColor(palette, 0xFFFDFCF7, "milkywhite");
        addColor(palette, 0xFFFEFEFA, "porcelain");
        addColor(palette, 0xFFF8EDE3, "shell");
        addColor(palette, 0xFFFBFBF8, "cotton");
        addColor(palette, 0xFFFFFEF7, "paperwhite");
        addColor(palette, 0xFFFFF9E6, "warmwhite");
        addColor(palette, 0xFFFFFCF2, "softwhite");
        addColor(palette, 0xFFF7F3E8, "offwhite");
        addColor(palette, 0xFFFFF7E0, "ivorycream");
        addColor(palette, 0xFFF7E7CE, "champagnewhite");
        addColor(palette, 0xFFF6EFC7, "vanillacreem");
        addColor(palette, 0xFFFFFFF5, "lightivory");

        // Reds.
        addColor(palette, 0xFFFF0000, "r", "red");
        addColor(palette, 0xFF8B0000, "dr", "darkred");
        addColor(palette, 0xFFB22222, "firebrick");
        addColor(palette, 0xFF8F3B2E, "brickred");
        addColor(palette, 0xFFB31B1B, "scarlet");
        addColor(palette, 0xFFE34234, "cinnabar");
        addColor(palette, 0xFFE34234, "vermilion");
        addColor(palette, 0xFFB81D13, "signalred");
        addColor(palette, 0xFFE60026, "safetyred");
        addColor(palette, 0xFFCF142B, "stopred");
        addColor(palette, 0xFFCD5C5C, "indianred");
        addColor(palette, 0xFFF08080, "lightcoral", "lcoral");
        addColor(palette, 0xFFFA8072, "salmon");
        addColor(palette, 0xFFE9967A, "darksalmon");
        addColor(palette, 0xFFFFA07A, "lightsalmon");
        addColor(palette, 0xFFDC143C, "crimson");
        addColor(palette, 0xFF660000, "bloodred");
        addColor(palette, 0xFF9B111E, "rubyred");
        addColor(palette, 0xFF922B3E, "currant");
        addColor(palette, 0xFF733635, "garnet");
        addColor(palette, 0xFFC41E3A, "cardinal");
        addColor(palette, 0xFFD2042D, "cherryred");
        addColor(palette, 0xFFA91B0D, "applered");
        addColor(palette, 0xFF65000B, "rosewood");
        addColor(palette, 0xFF4A0404, "oxblood");
        addColor(palette, 0xFF800020, "burgundy");
        addColor(palette, 0xFFC71585, "mediumvioletred");
        addColor(palette, 0xFFDB7093, "palevioletred");
        addColor(palette, 0xFF800000, "maroon");
        addColor(palette, 0xFF560319, "darkscarlet");
        addColor(palette, 0xFF7B0000, "deepred");
        addColor(palette, 0xFFFFC0CB, "p", "pink");
        addColor(palette, 0xFFFFB6C1, "lightpink", "lp");
        addColor(palette, 0xFFFF69B4, "hotpink");
        addColor(palette, 0xFFFF1493, "deeppink", "dpink");
        addColor(palette, 0xFFFFE4E1, "mistyrose");
        addColor(palette, 0xFFFFCDD2, "rose");
        addColor(palette, 0xFFE6A8D7, "dustypink");
        addColor(palette, 0xFFFF6347, "tomatered");
        addColor(palette, 0xFFFF5349, "redorange");
        addColor(palette, 0xFF5A2A41, "wineberry");
        addColor(palette, 0xFF70193D, "mulberryred");
        addColor(palette, 0xFF9C3B2E, "brick");
        addColor(palette, 0xFFB66A50, "clayred");

        // Pinks and rose tones.
        addColor(palette, 0xFFFF66CC, "rosepink");
        addColor(palette, 0xFFF29CA3, "blush");
        addColor(palette, 0xFFF4C2C2, "blushpink", "babypink");
        addColor(palette, 0xFFF49AC2, "pastelpink");
        addColor(palette, 0xFFFFBCD9, "cottoncandy");
        addColor(palette, 0xFFFFA6C9, "carnationpink");
        addColor(palette, 0xFFFC8EAC, "flamingopink");
        addColor(palette, 0xFFFC6C85, "watermelonpink");
        addColor(palette, 0xFFFC5A8D, "strawberrypink");
        addColor(palette, 0xFFE77CA8, "peony");
        addColor(palette, 0xFF915F6D, "mauverose");
        addColor(palette, 0xFFE8B7C7, "softrose");
        addColor(palette, 0xFFC08081, "rosedust");
        addColor(palette, 0xFFAA98A9, "rosequartz");
        addColor(palette, 0xFFFAD6D0, "shellpink");
        addColor(palette, 0xFFF7CAD0, "powderpink");
        addColor(palette, 0xFFF6A6B2, "petalpink");
        addColor(palette, 0xFFFF8FAB, "warmpink");
        addColor(palette, 0xFFFFB3DE, "coolpink");
        addColor(palette, 0xFFF88379, "pinkcoral");
        addColor(palette, 0xFFF9D5E5, "lightrose");

        // Orange / amber / yellow.
        addColor(palette, 0xFFFF4500, "orangered");
        addColor(palette, 0xFFFF6347, "tomato");
        addColor(palette, 0xFFFF7F50, "coral");
        addColor(palette, 0xFFFF8C00, "darkorange", "dorange");
        addColor(palette, 0xFFFFA500, "o", "orange");
        addColor(palette, 0xFFFFB347, "lightorange");
        addColor(palette, 0xFFFFC300, "amber");
        addColor(palette, 0xFFF28500, "burntorange");
        addColor(palette, 0xFFCC5500, "burntorange2");
        addColor(palette, 0xFFB5651D, "rust");
        addColor(palette, 0xFFE67E22, "carrot");
        addColor(palette, 0xFFD35400, "pumpkin");
        addColor(palette, 0xFFF4A300, "marigold");
        addColor(palette, 0xFFFFC87C, "apricot");
        addColor(palette, 0xFFFF9966, "tangerine");
        addColor(palette, 0xFFFF7518, "safetyorange");
        addColor(palette, 0xFFEC5800, "persimmon");
        addColor(palette, 0xFFF37A48, "mandarin");
        addColor(palette, 0xFFDA8A67, "copperorange");
        addColor(palette, 0xFFFF8F4F, "autumnorange");
        addColor(palette, 0xFFFD5E53, "sunsetorange");
        addColor(palette, 0xFFFFB347, "mango");
        addColor(palette, 0xFFFFD1A3, "papaya");
        addColor(palette, 0xFFFFCBA4, "cantaloupe");
        addColor(palette, 0xFFFDBCB4, "melon");
        addColor(palette, 0xFFFFA000, "orangepeel");
        addColor(palette, 0xFFFDBA4D, "honeyorange");
        addColor(palette, 0xFFFFD700, "gold");
        addColor(palette, 0xFFDAA520, "goldenrod");
        addColor(palette, 0xFFB8860B, "darkgoldenrod");
        addColor(palette, 0xFFEEE8AA, "palegoldenrod");
        addColor(palette, 0xFFFFFACD, "lemonchiffon");
        addColor(palette, 0xFFFFF8E1, "lightcream");
        addColor(palette, 0xFFFFFF00, "y", "yellow");
        addColor(palette, 0xFFFFFFE0, "lightyellow", "ly");
        addColor(palette, 0xFFFAFAD2, "lightgoldenrodyellow");
        addColor(palette, 0xFFF0E68C, "khaki");
        addColor(palette, 0xFFBDB76B, "darkkhaki");
        addColor(palette, 0xFFFFF44F, "canary");
        addColor(palette, 0xFFFFEF00, "lemon");
        addColor(palette, 0xFFFFE135, "banana");
        addColor(palette, 0xFFE1AD01, "mustard");
        addColor(palette, 0xFFC9AE5D, "sand");
        addColor(palette, 0xFFFFDA03, "maize");
        addColor(palette, 0xFFFFF1A8, "butteryellow");
        addColor(palette, 0xFFFFD800, "schoolbusyellow");
        addColor(palette, 0xFFF0E130, "dandelion");
        addColor(palette, 0xFFF4C430, "saffron");
        addColor(palette, 0xFFFCE883, "honeyyellow");
        addColor(palette, 0xFFFBEC5D, "cornyellow");
        addColor(palette, 0xFFE4D96F, "strawyellow");
        addColor(palette, 0xFFE6BE5A, "ambergold");
        addColor(palette, 0xFFFFC72C, "brightgold");
        addColor(palette, 0xFFFFDE59, "warmyellow");

        // Browns / tans.
        addColor(palette, 0xFFA52A2A, "n", "br", "brown");
        addColor(palette, 0xFF8B4513, "saddlebrown");
        addColor(palette, 0xFFA0522D, "sienna");
        addColor(palette, 0xFFD2691E, "chocolate");
        addColor(palette, 0xFFCD853F, "peru");
        addColor(palette, 0xFFF4A460, "sandybrown");
        addColor(palette, 0xFFBC8F8F, "rosybrown");
        addColor(palette, 0xFFD2B48C, "tan");
        addColor(palette, 0xFFF5DEB3, "wheat");
        addColor(palette, 0xFFDEB887, "burlywood");
        addColor(palette, 0xFFC19A6B, "camel");
        addColor(palette, 0xFF967969, "mocha");
        addColor(palette, 0xFF6F4E37, "coffee");
        addColor(palette, 0xFF3B2F2F, "espresso");
        addColor(palette, 0xFF7B3F00, "copperbrown");
        addColor(palette, 0xFF635147, "umber");
        addColor(palette, 0xFF826644, "rawumber");
        addColor(palette, 0xFF8A3324, "burntumber");
        addColor(palette, 0xFF704214, "sepia");
        addColor(palette, 0xFF4A0100, "mahogany");
        addColor(palette, 0xFF5D432C, "walnut");
        addColor(palette, 0xFF954535, "chestnut");
        addColor(palette, 0xFF7E5E3C, "acorn");
        addColor(palette, 0xFF8E6F4E, "pecan");
        addColor(palette, 0xFF8E7618, "hazelbrown");
        addColor(palette, 0xFF7B4A12, "nutmeg");
        addColor(palette, 0xFFD2691E, "cinnamon");
        addColor(palette, 0xFFB9895A, "toast", "toastbrown");
        addColor(palette, 0xFF8B5A2B, "maple");
        addColor(palette, 0xFF6B4423, "oak");
        addColor(palette, 0xFF7A5230, "woodbrown");
        addColor(palette, 0xFF7C5A3C, "earthbrown");
        addColor(palette, 0xFF6B4F3A, "dirtbrown");
        addColor(palette, 0xFF70543E, "mud");
        addColor(palette, 0xFFB66A50, "clay");
        addColor(palette, 0xFFE2725B, "terracotta");
        addColor(palette, 0xFF8A4B3A, "reddishbrown");
        addColor(palette, 0xFFC9A27E, "darktan");
        addColor(palette, 0xFFD8B98A, "lighttan");
        addColor(palette, 0xFFB57A42, "toffee");
        addColor(palette, 0xFFC68E17, "caramel");
        addColor(palette, 0xFFE1A95F, "butterscotch");
        addColor(palette, 0xFFF4A460, "sandybrown2");
        addColor(palette, 0xFF8C7853, "bronzebrown");
        addColor(palette, 0xFF7C4A2D, "leatherbrown");

        // Skin tones and flesh neutrals.
        addColor(palette, 0xFFFFE0BD, "fairskin");
        addColor(palette, 0xFFFFE8D6, "palefair");
        addColor(palette, 0xFFFDEFE3, "porcelainskin");
        addColor(palette, 0xFFF8E7D1, "ivoryskin");
        addColor(palette, 0xFFF1C27D, "lightskin");
        addColor(palette, 0xFFE8D1B5, "softbeige");
        addColor(palette, 0xFFDDB892, "warmbeige");
        addColor(palette, 0xFFD8B899, "neutralbeige");
        addColor(palette, 0xFFE8C1B0, "roseskin");
        addColor(palette, 0xFFFFDAB3, "peachskin");
        addColor(palette, 0xFFF2C6A0, "peachbeige");
        addColor(palette, 0xFFFFD1A6, "lightpeach");
        addColor(palette, 0xFFE0AC69, "mediumskin");
        addColor(palette, 0xFFD9A066, "sunbeige");
        addColor(palette, 0xFFD6A77A, "goldenbeige");
        addColor(palette, 0xFFC68642, "tanbeige");
        addColor(palette, 0xFFB8895F, "warmtan");
        addColor(palette, 0xFFA76F4E, "caramelskin");
        addColor(palette, 0xFFB87333, "honeyskin");
        addColor(palette, 0xFFB6A27B, "olivebeige");
        addColor(palette, 0xFF8D5524, "bronzeskin");
        addColor(palette, 0xFF7A4B2A, "deepbronze");
        addColor(palette, 0xFF6F4E37, "richbrownskin");
        addColor(palette, 0xFF86654E, "umbertan");
        addColor(palette, 0xFF5C3A21, "mahoganyskin");
        addColor(palette, 0xFFD8C3A5, "coolbeige");
        addColor(palette, 0xFFC9AE8D, "dustbeige");
        addColor(palette, 0xFFE7C9A9, "almondskin");
        addColor(palette, 0xFFC8A27A, "latte");
        addColor(palette, 0xFF9C6B4E, "cocoatan");

        // Greens.
        addColor(palette, 0xFF006400, "darkgreen", "dg");
        addColor(palette, 0xFF008000, "green");
        addColor(palette, 0xFF00FF00, "g", "lime");
        addColor(palette, 0xFF32CD32, "limegreen");
        addColor(palette, 0xFF228B22, "forestgreen");
        addColor(palette, 0xFF2E8B57, "seagreen");
        addColor(palette, 0xFF3CB371, "mediumseagreen");
        addColor(palette, 0xFF20B2AA, "lightseagreen");
        addColor(palette, 0xFF66CDAA, "mediumaquamarine");
        addColor(palette, 0xFF8FBC8F, "darkseagreen");
        addColor(palette, 0xFF90EE90, "lightgreen", "lg");
        addColor(palette, 0xFF98FB98, "palegreen");
        addColor(palette, 0xFF00FA9A, "mediumspringgreen");
        addColor(palette, 0xFF00FF7F, "springgreen");
        addColor(palette, 0xFF7CFC00, "lawngreen");
        addColor(palette, 0xFF7FFF00, "chartreuse");
        addColor(palette, 0xFFADFF2F, "greenyellow");
        addColor(palette, 0xFF9ACD32, "yellowgreen");
        addColor(palette, 0xFF6B8E23, "olivedrab");
        addColor(palette, 0xFF556B2F, "darkolivegreen");
        addColor(palette, 0xFF808000, "olive");
        addColor(palette, 0xFF4F7942, "fern");
        addColor(palette, 0xFF50C878, "emerald");
        addColor(palette, 0xFF0E9F6E, "darkemerald");
        addColor(palette, 0xFF2AAA8A, "junglegreen");
        addColor(palette, 0xFF93C572, "pistachio");
        addColor(palette, 0xFFB2EC5D, "inchworm");
        addColor(palette, 0xFFACE1AF, "celadon");
        addColor(palette, 0xFF01796F, "pinegreen");
        addColor(palette, 0xFF355E3B, "huntergreen");
        addColor(palette, 0xFF5F8575, "sage");
        addColor(palette, 0xFF4CBB17, "kellygreen");
        addColor(palette, 0xFF00A86B, "jade");
        addColor(palette, 0xFFF0FFF0, "honeydew");
        addColor(palette, 0xFFF5FFFA, "mintcream");
        addColor(palette, 0xFF8A9A5B, "moss", "mossgreen");
        addColor(palette, 0xFF4CBB17, "grassgreen");
        addColor(palette, 0xFF6AA84F, "leafgreen");
        addColor(palette, 0xFF3EA055, "clover");
        addColor(palette, 0xFF009E60, "shamrock");
        addColor(palette, 0xFF5F8A4D, "basil");
        addColor(palette, 0xFF98FF98, "mintleaf");
        addColor(palette, 0xFFD1E231, "peargreen");
        addColor(palette, 0xFF8DB600, "applegreen");
        addColor(palette, 0xFF568203, "avocado");
        addColor(palette, 0xFF8EE53F, "kiwi");
        addColor(palette, 0xFF227442, "cactus");
        addColor(palette, 0xFF4F6F52, "swampgreen");
        addColor(palette, 0xFF556B2F, "boggreen", "armygreen");
        addColor(palette, 0xFF78866B, "camogreen");
        addColor(palette, 0xFF55624C, "darkmoss");
        addColor(palette, 0xFFA8C686, "lightmoss");
        addColor(palette, 0xFF2D5D34, "spruce");
        addColor(palette, 0xFF05472A, "evergreen");
        addColor(palette, 0xFF1E5B3A, "firgreen");
        addColor(palette, 0xFF2A5B3F, "needlegreen");
        addColor(palette, 0xFF9FE2BF, "seafoam", "seafoamgreen", "foamgreen");
        addColor(palette, 0xFF98FF98, "mintgreen");
        addColor(palette, 0xFF77DD77, "pastelgreen");
        addColor(palette, 0xFF40826D, "viridian");
        addColor(palette, 0xFF0BDA51, "malachite");
        addColor(palette, 0xFF76FF7A, "absinthe");
        addColor(palette, 0xFF39FF14, "neongreen");

        // Cyans / teals / turquoises.
        addColor(palette, 0xFF008080, "teal");
        addColor(palette, 0xFF005F5F, "darkteal");
        addColor(palette, 0xFF66B2B2, "lightteal");
        addColor(palette, 0xFF5F9EA0, "softteal");
        addColor(palette, 0xFF006D6F, "deepteal");
        addColor(palette, 0xFF00FFFF, "c", "aqua", "cyan");
        addColor(palette, 0xFFE0FFFF, "lightcyan");
        addColor(palette, 0xFFAFEEEE, "paleturquoise");
        addColor(palette, 0xFF7FFFD4, "aquamarine");
        addColor(palette, 0xFF40E0D0, "turquoise");
        addColor(palette, 0xFF48D1CC, "mediumturquoise");
        addColor(palette, 0xFF00CED1, "darkturquoise");
        addColor(palette, 0xFF5F9EA0, "cadetblue");
        addColor(palette, 0xFF00A4A6, "lagoon");
        addColor(palette, 0xFF73C2FB, "maya");
        addColor(palette, 0xFF76D7EA, "iceblue");
        addColor(palette, 0xFF00CCCC, "robinsegg", "robinseggblue");
        addColor(palette, 0xFF0ABAB5, "tiffany", "tiffanyblue");
        addColor(palette, 0xFF9FE2BF, "seafoamcyan");
        addColor(palette, 0xFF78C7C7, "glacier", "glacierblue");
        addColor(palette, 0xFF008C99, "lagoonblue");
        addColor(palette, 0xFF00CC99, "caribbeangreen");
        addColor(palette, 0xFFAEEEEE, "oceanfoam");
        addColor(palette, 0xFFAAF0D1, "mintcyan");
        addColor(palette, 0xFF7FDBFF, "coolcyan");
        addColor(palette, 0xFF008B8B, "deepcyan");
        addColor(palette, 0xFF00F5FF, "brightcyan");
        addColor(palette, 0xFFD7FFFF, "arcticcyan");
        addColor(palette, 0xFF5DADEC, "poolblue", "waterblue");
        addColor(palette, 0xFF0D98BA, "bluegreen");
        addColor(palette, 0xFF088F8F, "greenblue");

        // Blues.
        addColor(palette, 0xFF000080, "navy");
        addColor(palette, 0xFF191970, "midnightblue");
        addColor(palette, 0xFF00008B, "darkblue", "db");
        addColor(palette, 0xFF0000CD, "mediumblue");
        addColor(palette, 0xFF0000FF, "u", "blue");
        addColor(palette, 0xFF4169E1, "royalblue");
        addColor(palette, 0xFF1E90FF, "dodgerblue");
        addColor(palette, 0xFF6495ED, "cornflowerblue");
        addColor(palette, 0xFF4682B4, "steelblue");
        addColor(palette, 0xFF87CEEB, "skyblue");
        addColor(palette, 0xFF87CEFA, "lightskyblue");
        addColor(palette, 0xFFADD8E6, "lb", "lightblue");
        addColor(palette, 0xFFB0E0E6, "powderblue");
        addColor(palette, 0xFFB0C4DE, "lightsteelblue");
        addColor(palette, 0xFF87CEFA, "lsb");
        addColor(palette, 0xFF00BFFF, "deepskyblue");
        addColor(palette, 0xFFF0F8FF, "aliceblue");
        addColor(palette, 0xFF5DADE2, "softblue");
        addColor(palette, 0xFF3498DB, "brightblue");
        addColor(palette, 0xFF2980B9, "deepblue");
        addColor(palette, 0xFF89CFF0, "babyblue");
        addColor(palette, 0xFF7393B3, "airforceblue");
        addColor(palette, 0xFF6082B6, "glaucous");
        addColor(palette, 0xFFCCCCFF, "periwinkle");
        addColor(palette, 0xFF0047AB, "cobalt");
        addColor(palette, 0xFF1560BD, "denim");
        addColor(palette, 0xFF1F75FE, "azureblue");
        addColor(palette, 0xFFF0FFFF, "azure");
        addColor(palette, 0xFF2A52BE, "cerulean");
        addColor(palette, 0xFF007BA7, "ceruleanblue");
        addColor(palette, 0xFF3F00FF, "ultramarine");
        addColor(palette, 0xFF26619C, "lapis", "lapisblue");
        addColor(palette, 0xFF7DF9FF, "electricblue");
        addColor(palette, 0xFF4D4DFF, "neonblue");
        addColor(palette, 0xFF4F42B5, "oceanblue");
        addColor(palette, 0xFF01386A, "marineblue");
        addColor(palette, 0xFF4B6F8C, "stormblue");
        addColor(palette, 0xFF5D8AA8, "rainblue");
        addColor(palette, 0xFFB9D9EB, "arcticblue");
        addColor(palette, 0xFFB4D9F7, "frostblue");
        addColor(palette, 0xFF6D9BC3, "dustblue");
        addColor(palette, 0xFF6C7A89, "mutedblue");
        addColor(palette, 0xFF6B7A8F, "slatebluegray");
        addColor(palette, 0xFF003366, "inkblue");
        addColor(palette, 0xFF001F54, "deepnavy");
        addColor(palette, 0xFF5C6BC0, "twilightblue");
        addColor(palette, 0xFF111E6C, "nightblue");
        addColor(palette, 0xFF73A9C2, "moonblue");
        addColor(palette, 0xFFA4C8E1, "washedblue");
        addColor(palette, 0xFF3B4D61, "softnavy");
        addColor(palette, 0xFF5DA9E9, "coolblue");
        addColor(palette, 0xFFA2A2D0, "bluebell");
        addColor(palette, 0xFF3B82A0, "harborblue");
        addColor(palette, 0xFF1F5A7A, "portblue");
        addColor(palette, 0xFFD8F1FF, "winterblue");
        addColor(palette, 0xFFA9D6E5, "polarblue");

        // Purple / violet / magenta.
        addColor(palette, 0xFF4B0082, "indigo");
        addColor(palette, 0xFF663399, "rebeccapurple");
        addColor(palette, 0xFF800080, "purple");
        addColor(palette, 0xFF8A2BE2, "blueviolet");
        addColor(palette, 0xFF9400D3, "darkviolet");
        addColor(palette, 0xFF9932CC, "darkorchid");
        addColor(palette, 0xFFBA55D3, "mediumorchid");
        addColor(palette, 0xFF9370DB, "mediumpurple");
        addColor(palette, 0xFF7B68EE, "mediumslateblue");
        addColor(palette, 0xFF6A5ACD, "slateblue");
        addColor(palette, 0xFF483D8B, "darkslateblue");
        addColor(palette, 0xFF8B008B, "darkmagenta");
        addColor(palette, 0xFFFF00FF, "m", "fuchsia", "magenta");
        addColor(palette, 0xFFEE82EE, "violet");
        addColor(palette, 0xFFDDA0DD, "plum");
        addColor(palette, 0xFFDA70D6, "orchid");
        addColor(palette, 0xFFE6E6FA, "lavender");
        addColor(palette, 0xFFD8BFD8, "thistle");
        addColor(palette, 0xFFB57EDC, "lavenderpurple");
        addColor(palette, 0xFFC8A2C8, "lilac");
        addColor(palette, 0xFF9F2B68, "raspberry");
        addColor(palette, 0xFFDE3163, "cerise");
        addColor(palette, 0xFFFF77FF, "bubblegummagenta");
        addColor(palette, 0xFFFF66CC, "bubblegumpink");
        addColor(palette, 0xFFC54B8C, "mulberry");
        addColor(palette, 0xFF673147, "mulberrypurple");
        addColor(palette, 0xFFE0B0FF, "mauve");
        addColor(palette, 0xFFB784A7, "dustymauve");
        addColor(palette, 0xFF8B395A, "jam");
        addColor(palette, 0xFF873260, "boysenberry");
        addColor(palette, 0xFF4D184D, "blackberry");
        addColor(palette, 0xFF7A4E8A, "berrypurple");
        addColor(palette, 0xFF7851A9, "royalpurple");
        addColor(palette, 0xFF602F6B, "imperialpurple");
        addColor(palette, 0xFFDF73FF, "heliotrope");
        addColor(palette, 0xFF5A4FCF, "iris");
        addColor(palette, 0xFFC9A0DC, "wisteria");
        addColor(palette, 0xFFB19CD9, "periwinklepurple");
        addColor(palette, 0xFFA87DC2, "softviolet");
        addColor(palette, 0xFF6F2DA8, "deepviolet");
        addColor(palette, 0xFFD8B7FF, "pastelpurple");
        addColor(palette, 0xFF7E57C2, "coolviolet");
        addColor(palette, 0xFF9C6ADE, "warmviolet");
        addColor(palette, 0xFFE29CD2, "orchidpink");
        addColor(palette, 0xFF9B30A6, "plummagenta");

        // Readable game-art / UI accent colors.
        addColor(palette, 0xFF1ABC9C, "mint");
        addColor(palette, 0xFF16A085, "deepmint");
        addColor(palette, 0xFF2ECC71, "brightgreen");
        addColor(palette, 0xFF27AE60, "deepgreen");

        // Extra utility accent colors.
        addColor(palette, 0xFFBDC3C7, "cloud");
        addColor(palette, 0xFF95A5A6, "concrete");
        addColor(palette, 0xFF7F8C8D, "asphaltgray");
        addColor(palette, 0xFF34495E, "wetasphalt");
        addColor(palette, 0xFF2C3E50, "midnight");
        addColor(palette, 0xFFE74C3C, "alizarin");
        addColor(palette, 0xFFC0392B, "darkalizarin");
        addColor(palette, 0xFFF39C12, "sunflower");
        addColor(palette, 0xFF9B59B6, "amethyst");
        addColor(palette, 0xFF8E44AD, "darkamethyst");
        addColor(palette, 0xFFFF7F7F, "softred");
        addColor(palette, 0xFF7FB3FF, "pastelblue");
        addColor(palette, 0xFF77DD77, "pastelgreen");
        addColor(palette, 0xFFF49AC2, "pastelpink");
        addColor(palette, 0xFFFFB347, "pastelorange");
        addColor(palette, 0xFFAEC6CF, "pastelcyan");
        addColor(palette, 0xFFFDFD96, "pastelyellow");
        addColor(palette, 0xFFB76E79, "rosegold");
        addColor(palette, 0xFF0F52BA, "sapphire");
        addColor(palette, 0xFF50C878, "emeraldgreen");
        addColor(palette, 0xFFE0115F, "ruby");
        addColor(palette, 0xFF614051, "eggplant");
        addColor(palette, 0xFF722F37, "wine");
        addColor(palette, 0xFF6F2DA8, "grape");
        addColor(palette, 0xFF36454F, "slatecharcoal");
        addColor(palette, 0xFFCD7F32, "bronze");
        addColor(palette, 0xFF8C6239, "darkbronze");
        addColor(palette, 0xFFD6A65A, "lightbronze");
        addColor(palette, 0xFFB87333, "copper");
        addColor(palette, 0xFF7E6E60, "agedcopper");
        addColor(palette, 0xFF71797E, "steel");
        addColor(palette, 0xFF43464B, "darksteel");
        addColor(palette, 0xFFA7B2B9, "lightsteel");
        addColor(palette, 0xFFB5B9BE, "iron");
        addColor(palette, 0xFF4B4E53, "castiron");
        addColor(palette, 0xFF727472, "nickel");
        addColor(palette, 0xFFE8F1F2, "chrome");
        addColor(palette, 0xFFC0C0C0, "silvermetal");
        addColor(palette, 0xFFD4AF37, "goldmetal");
        addColor(palette, 0xFFCFB53B, "oldgold");
        addColor(palette, 0xFFB5A642, "brass");
        addColor(palette, 0xFFCD9575, "antiquebrass");
        addColor(palette, 0xFFE6BE8A, "palegold");
        addColor(palette, 0xFFF8F6D8, "whitegold");
        addColor(palette, 0xFFE5E4E2, "platinumwhite");
        addColor(palette, 0xFF0B0B0B, "obsidian");
        addColor(palette, 0xFF676767, "granite");
        addColor(palette, 0xFF4C4F56, "basalt");
        addColor(palette, 0xFFF2F3F4, "marble");
        addColor(palette, 0xFFC2B280, "sandstone");
        addColor(palette, 0xFFDCD3B9, "limestone");
        addColor(palette, 0xFF6B6B6B, "shale");
        addColor(palette, 0xFFF6F3EA, "quartz");
        addColor(palette, 0xFF00A86B, "jadestone");
        addColor(palette, 0xFF9966CC, "amethyststone");
        addColor(palette, 0xFFE0115F, "rubystone");
        addColor(palette, 0xFF50C878, "emeraldstone");

        // Extra pastel aliases and soft accents.
        addColor(palette, 0xFFFF6961, "pastelred");
        addColor(palette, 0xFFBFFF00, "pastellime");
        addColor(palette, 0xFF99E6E6, "pastelteal");
        addColor(palette, 0xFFAAF0D1, "pastelmint");
        addColor(palette, 0xFFDCD0FF, "pastellavender");
        addColor(palette, 0xFFFFD1DC, "pastelrose");
        addColor(palette, 0xFFFFDAB9, "pastelpeach");
        addColor(palette, 0xFFFFF5CC, "pastelcream");
        addColor(palette, 0xFFF5E6CC, "pastelbeige");
        addColor(palette, 0xFFFFDAB9, "palepeach");
        addColor(palette, 0xFFBFD7ED, "paleblue");
        addColor(palette, 0xFFCCFFCC, "palemint");
        addColor(palette, 0xFFE0BBE4, "palelilac");
        addColor(palette, 0xFFFFD8B1, "softpeach");
        addColor(palette, 0xFFE6E6FA, "softlavender");
        addColor(palette, 0xFFFFF7AE, "softyellow");
        addColor(palette, 0xFFCFFAFE, "softcyan");
        addColor(palette, 0xFFC1E1C1, "softgreen");
        addColor(palette, 0xFFFFB07C, "softorange");
        addColor(palette, 0xFFFF5CCD, "softmagenta");
        addColor(palette, 0xFFA188C5, "softbrown");
        addColor(palette, 0xFFB8B8B8, "softgray");

        // Game art and utility accents.
        addColor(palette, 0xFFFFFFFF, "uiwhite");
        addColor(palette, 0xFF000000, "uiblack");
        addColor(palette, 0xFF808080, "uigray");
        addColor(palette, 0xFF4A90E2, "uiblue");
        addColor(palette, 0xFF2ECC71, "uigreen");
        addColor(palette, 0xFFFFD700, "uiyellow");
        addColor(palette, 0xFFE74C3C, "uired", "alertred");
        addColor(palette, 0xFFFFC107, "warningyellow");
        addColor(palette, 0xFFFF9800, "warningorange");
        addColor(palette, 0xFF2ECC71, "successgreen");
        addColor(palette, 0xFF3498DB, "infoblue");
        addColor(palette, 0xFF4285F4, "selectionblue");
        addColor(palette, 0xFFFFEB3B, "highlightyellow");
        addColor(palette, 0xFF00E5FF, "accentcyan");
        addColor(palette, 0xFFFF6EC7, "accentpink");
        addColor(palette, 0xFFB388FF, "accentpurple");
        addColor(palette, 0xFF69F0AE, "accentgreen");
        addColor(palette, 0xFF6B7280, "shadowgray");
        addColor(palette, 0xFF1F2937, "deepshadow");
        addColor(palette, 0xFF4B5563, "panelgray");
        addColor(palette, 0xFF89CFF0, "screenblue");
        addColor(palette, 0xFF39FF14, "terminalgreen", "matrixgreen");
        addColor(palette, 0xFFFF4FD8, "radarpink");
        addColor(palette, 0xFF00FF99, "hudgreen");
        addColor(palette, 0xFF00E5FF, "hudcyan");
        addColor(palette, 0xFFFF8C42, "hudorange");
        addColor(palette, 0xFFFF3131, "hudred");
        addColor(palette, 0xFFFF10F0, "neonpink");
        addColor(palette, 0xFFBC13FE, "neonpurple");
        addColor(palette, 0xFF00FFFF, "neoncyan");
        addColor(palette, 0xFFFFFF33, "neonyellow");
        addColor(palette, 0xFFFF5F1F, "neonorange");
        addColor(palette, 0xFF4B6EFF, "arcaneblue");
        addColor(palette, 0xFF6C63FF, "mana", "manablue");
        addColor(palette, 0xFF7FFF00, "poisongreen", "acidgreen", "toxicgreen");
        addColor(palette, 0xFFCF1020, "lava");
        addColor(palette, 0xFFFF4500, "lavaorange");
        addColor(palette, 0xFFFF6B35, "ember");
        addColor(palette, 0xFFE25822, "emberred");
        addColor(palette, 0xFFB2BEB5, "ash");
        addColor(palette, 0xFFC2B280, "dust");
        addColor(palette, 0xFFE5E7EB, "fog");
        addColor(palette, 0xFFDDE7EE, "mist");

        // Nature, sky, water, and atmosphere colors.
        addColor(palette, 0xFF87CEEB, "sky");
        addColor(palette, 0xFFFFF2B2, "daylight");
        addColor(palette, 0xFFFFCBA4, "dawn");
        addColor(palette, 0xFFFFB56B, "sunrise");
        addColor(palette, 0xFFFF7E5F, "sunset");
        addColor(palette, 0xFF6F5FA7, "twilight");
        addColor(palette, 0xFF7A8799, "stormcloud");
        addColor(palette, 0xFF738496, "raincloud");
        addColor(palette, 0xFFB7C0C7, "overcast");
        addColor(palette, 0xFFBFD7EA, "mistblue");
        addColor(palette, 0xFFD6EAF8, "fogblue");
        addColor(palette, 0xFFEAF7FF, "frost");
        addColor(palette, 0xFFDFF6FF, "ice");
        addColor(palette, 0xFFF8FCFF, "glacierwhite");
        addColor(palette, 0xFF004B6B, "deepwater");
        addColor(palette, 0xFF5DADEC, "shallowwater");
        addColor(palette, 0xFF3C78D8, "riverblue");
        addColor(palette, 0xFF4A86E8, "lakeblue");
        addColor(palette, 0xFFB2EBF2, "oceanmist");
        addColor(palette, 0xFF006994, "seablue");
        addColor(palette, 0xFF00416A, "seadeep");
        addColor(palette, 0xFFFFFFFF, "foamwhite");
        addColor(palette, 0xFFE2C290, "shoresand");
        addColor(palette, 0xFFB08968, "dunebrown");
        addColor(palette, 0xFFC2B280, "drygrass");
        addColor(palette, 0xFF7CFC00, "freshgrass");
        addColor(palette, 0xFF5DA130, "meadowgreen");
        addColor(palette, 0xFF6B4F3A, "pinebrown");
        addColor(palette, 0xFF5C4033, "treebark");
        addColor(palette, 0xFF4F6F52, "leafshadow");
        addColor(palette, 0xFF9ACD32, "sunlitleaf");
        addColor(palette, 0xFF0C1445, "nightsky");
        addColor(palette, 0xFFE6E6FA, "moonlight");
        addColor(palette, 0xFFF8F8FF, "starlight");

        // Additional extended neutrals.
        addColor(palette, 0xFFB38B6D, "taupe");
        addColor(palette, 0xFFB7B09C, "greige");
        addColor(palette, 0xFF8D918D, "stone");
        addColor(palette, 0xFFB0A99F, "putty");
        addColor(palette, 0xFFBEBFC5, "smoke");
        addColor(palette, 0xFFF5F5F5, "smokewhite");
        addColor(palette, 0xFFA89984, "warmbeige2");
        addColor(palette, 0xFF6B6F59, "drab");
        addColor(palette, 0xFFE5C29F, "fawn");
        addColor(palette, 0xFFBFA6A0, "mushroom");
        addColor(palette, 0xFF8E6F63, "truffle");
        addColor(palette, 0xFF8A7967, "ashbrown");
        addColor(palette, 0xFFC4A484, "dryclay");
        addColor(palette, 0xFFD7B49E, "paleclay");
        addColor(palette, 0xFFC2A58D, "mutedtan");
        addColor(palette, 0xFF8B6F5A, "mutedbrown");
        addColor(palette, 0xFF7E8B5A, "mutedolive");
        addColor(palette, 0xFFC8B560, "mutedgold");
        addColor(palette, 0xFFC08081, "mutedrose");
        addColor(palette, 0xFF8E7BAF, "mutedviolet");
        addColor(palette, 0xFF7BA7A9, "mutedcyan");
        addColor(palette, 0xFF5F8F8F, "mutedteal");
        addColor(palette, 0xFF6E8B6A, "mutedgreen");
        addColor(palette, 0xFFB66A6A, "mutedred");
        addColor(palette, 0xFFCC8A5A, "mutedorange");
        addColor(palette, 0xFFD6C66A, "mutedyellow");

        return Collections.unmodifiableMap(palette);
    }

    /**
     * Registers one color under one or more token names.
     *
     * <p>All tokens are normalized before insertion so palette definitions
     * stay consistent with runtime lookup behavior.</p>
     *
     * <p>This helper rejects duplicate aliases so palette growth remains
     * explicit and deterministic. Silent overwrite behavior would make the
     * token table fragile as it grows larger.</p>
     *
     * @param palette target palette map
     * @param argb packed ARGB color
     * @param tokens one or more token aliases for the color
     */
    private static void addColor(Map<String, Integer> palette, int argb, String... tokens) {
        if (palette == null) {
            throw new IllegalArgumentException("PixelTokenSupport.addColor: palette cannot be null.");
        }
        if (tokens == null || tokens.length == 0) {
            throw new IllegalArgumentException("PixelTokenSupport.addColor: at least one token is required.");
        }

        for (String rawToken : tokens) {
            if (rawToken == null || rawToken.isBlank()) {
                throw new IllegalArgumentException("PixelTokenSupport.addColor: palette token cannot be null or blank.");
            }

            String token = normalize(rawToken);
            Integer existing = palette.get(token);
            if (existing != null && existing.intValue() != argb) {
                throw new IllegalStateException(
                        "PixelTokenSupport.addColor: duplicate token '" + token + "' maps to multiple colors."
                );
            }
            palette.put(token, argb);
        }
    }
}
