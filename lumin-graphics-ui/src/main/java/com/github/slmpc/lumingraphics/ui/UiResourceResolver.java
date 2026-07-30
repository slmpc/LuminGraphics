package com.github.slmpc.lumingraphics.ui;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.text.TtfFontLoader;
import java.util.Map;
import java.util.Objects;

public interface UiResourceResolver {
    Render2DTexture texture(String id);
    TtfFontLoader font(String id);
    default Render2DTexture atlasTexture(Object texture) {
        if(texture instanceof Render2DTexture value)return value;
        throw new UiResourceNotFoundException("font atlas",String.valueOf(texture));
    }
    static UiResourceResolver of(Map<String,Render2DTexture> textures,Map<String,TtfFontLoader> fonts,String defaultFontId) {
        Map<String,Render2DTexture> textureSnapshot=Map.copyOf(textures);Map<String,TtfFontLoader> fontSnapshot=Map.copyOf(fonts);
        return new UiResourceResolver(){public Render2DTexture texture(String id){Render2DTexture value=textureSnapshot.get(Objects.requireNonNull(id));if(value==null)throw new UiResourceNotFoundException("texture",id);return value;}public TtfFontLoader font(String id){String key=id==null?defaultFontId:id;TtfFontLoader value=fontSnapshot.get(key);if(value==null)throw new UiResourceNotFoundException("font",String.valueOf(key));return value;}};
    }
}
