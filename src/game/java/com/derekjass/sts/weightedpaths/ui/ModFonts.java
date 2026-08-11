package com.derekjass.sts.weightedpaths.ui;

import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public final class ModFonts {

    private static final Logger logger = LogManager.getLogger(ModFonts.class.getName());

    public static BitmapFont body;
    public static BitmapFont header;

    private ModFonts() {
    }

    public static void initialize() {
        if (Settings.language == Settings.GameLanguage.ZHS) {
            body = FontHelper.tipBodyFont;
            header = FontHelper.tipHeaderFont;
            logger.info("ModFonts using game ZHS tip fonts.");
            return;
        }
        if (tryLoadChineseFont()) {
            return;
        }
        body = FontHelper.tipBodyFont;
        header = FontHelper.tipHeaderFont;
        logger.warn("ModFonts fell back to game tip fonts.");
    }

    private static boolean tryLoadChineseFont() {
        try {
            FileHandle fontFile = Gdx.files.internal("font/zhs/NotoSansMonoCJKsc-Regular.otf");
            if (!fontFile.exists()) {
                logger.warn("Chinese font file not found.");
                return false;
            }
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
            FreeTypeFontParameter param = new FreeTypeFontParameter();
            param.characters = FreeTypeFontGenerator.DEFAULT_CHARS + ModUiStrings.allFontCharacters()
                    + CardUiStrings.allFontCharacters();
            param.size = Math.round(22.0f * Settings.scale);
            body = generator.generateFont(param);
            param.size = Math.round(23.0f * Settings.scale);
            header = generator.generateFont(param);
            generator.dispose();
            logger.info("ModFonts loaded NotoSansMonoCJKsc with CJK glyphs.");
            return true;
        } catch (Exception e) {
            logger.error("ModFonts failed to load Chinese font.", e);
            return false;
        }
    }
}
