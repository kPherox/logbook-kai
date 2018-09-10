package logbook.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

/**
 * 2値画像
 */
public class BiImage {

    private final static int ADDRESS_BITS_PER_WORD = 6;

    /** color */
    private final int color;
    /** width */
    private final int width;
    /** height */
    private final int height;
    /** width word length */
    private final int wwl;
    /** height word length */
    private final int hwl;
    // (width/64)×height
    private long[] dataW;
    // (height/64)×width
    private long[] dataH;

    /**
     * {@code image}で指定された画像と{@code color}で指定された色からBiImageを構築します
     *
     * @param image ソース画像
     * @param color 黒色として認識する色
     */
    public BiImage(BufferedImage image, Color color) {
        this.color = color.getRGB() & 0xffffff; // truncate alpha
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.wwl = ((this.width - 1) >> ADDRESS_BITS_PER_WORD) + 1;
        this.hwl = ((this.height - 1) >> ADDRESS_BITS_PER_WORD) + 1;
        WritableRaster raster;
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            raster = image.getRaster();
        } else {
            BufferedImage newimg = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = newimg.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            raster = newimg.getRaster();
        }
        int[] data = (int[]) raster.getDataElements(0, 0, this.width, this.height, null);

        this.init(data, this.width, this.height, this.color);
    }

    // 初期化処理
    private void init(int[] data, int w, int h, int color) {
        this.dataW = new long[this.wwl * this.height];
        this.dataH = new long[this.hwl * this.width];
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int pixcel = data[x + (y * this.width)] & 0xffffff;
                if (this.test(this.color, pixcel)) {
                    this.dataW[(x >> ADDRESS_BITS_PER_WORD) + y * this.wwl] |= (1L << x);
                    this.dataH[(y >> ADDRESS_BITS_PER_WORD) + x * this.hwl] |= (1L << y);
                }
            }
        }
    }

    /**
     * 2値化します
     *
     * @param a {@link #BiImage(BufferedImage, Color)}で指定された色のRGB値
     * @param b テスト対象のピクセル
     * @return 2値化した結果
     */
    protected boolean test(int a, int b) {
        return a == b;
    }

    /**
     * このBiImageの内容をBufferedImageとして出力します
     *
     * @return BufferedImage
     */
    public BufferedImage dump() {
        BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = image.getRaster();
        int[] data = new int[this.width * this.height];

        int foreground = this.color;
        int background = this.color ^ 0xffffff;

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                data[x + y * this.width] = (this.dataW[(x >> ADDRESS_BITS_PER_WORD) + y * this.wwl] & (1L << x)) == 0
                                ? background
                                : foreground;
            }
        }
        raster.setDataElements(0, 0, this.width, this.height, data);
        return image;
    }

    /**
     * x,yで指定された座標が黒色であるかを調べます
     *
     * @param x X
     * @param y Y
     * @return x,yで指定された座標の色
     */
    public boolean get(int x, int y) {
        int idx = (x >> ADDRESS_BITS_PER_WORD) + y * this.wwl;
        return (this.dataW.length > idx) && (this.dataW[idx] & (1L << x)) != 0;
    }

    /**
     * x,yで指定された座標を原点としてx+width,yまで(を含まない)の線形が全て黒色であるかを調べます
     *
     * @param x X
     * @param y Y
     * @param width 調べる横幅
     * @return 全て黒色であればtrue
     */
    public boolean allW(int x, int y, int width) {
        return this.all(this.dataW, x, y, width, this.wwl);
    }

    /**
     * x,yで指定された座標を原点としてx+width,yまで(を含まない)の線形に黒色が含まれるかを調べます
     *
     * @param x X
     * @param y Y
     * @param width 調べる横幅
     * @return 黒色が含まれる場合true
     */
    public boolean anyW(int x, int y, int width) {
        return this.any(this.dataW, x, y, width, this.wwl);
    }

    /**
     * x,yで指定された座標を原点としてx,y+heightまで(を含まない)の線形が全て黒色であるかを調べます
     *
     * @param x X
     * @param y Y
     * @param height 調べる縦幅
     * @return 全て黒色であればtrue
     */
    public boolean allH(int x, int y, int height) {
        return this.all(this.dataH, y, x, height, this.hwl);
    }

    /**
     * x,yで指定された座標を原点としてx,y+heightまで(を含まない)の線形に黒色が含まれるかを調べます
     *
     * @param x X
     * @param y Y
     * @param height 調べる縦幅
     * @return 黒色が含まれる場合true
     */
    public boolean anyH(int x, int y, int height) {
        return this.all(this.dataH, y, x, height, this.hwl);
    }

    /**
     * x,yで指定された座標を原点としてx+width,y+heightまで(を含まない)の矩形が全て黒色であるかを調べます
     *
     * @param x X
     * @param y Y
     * @param width 調べる横幅
     * @param height 調べる縦幅
     * @return 全て黒色であればtrue
     */
    public boolean all(int x, int y, int width, int height) {
        int wcost = (((x + width - 1) >> ADDRESS_BITS_PER_WORD) + 1 - (x >> ADDRESS_BITS_PER_WORD)) * height;
        int hcost = (((y + height - 1) >> ADDRESS_BITS_PER_WORD) + 1 - (y >> ADDRESS_BITS_PER_WORD)) * width;
        if (wcost > hcost) {
            for (int i = x, max = x + width; i < max; i++) {
                if (!this.all(this.dataH, y, i, height, this.hwl)) {
                    return false;
                }
            }
        } else {
            for (int i = y, max = y + height; i < max; i++) {
                if (!this.all(this.dataW, x, i, width, this.wwl)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * x,yで指定された座標を原点としてx+width,y+heightまで(を含まない)の矩形に黒色が含まれるかを調べます
     *
     * @param x X
     * @param y Y
     * @param width 調べる横幅
     * @param height 調べる縦幅
     * @return 黒色が含まれる場合true
     */
    public boolean any(int x, int y, int width, int height) {
        int wcost = (((x + width - 1) >> ADDRESS_BITS_PER_WORD) + 1 - (x >> ADDRESS_BITS_PER_WORD)) * height;
        int hcost = (((y + height - 1) >> ADDRESS_BITS_PER_WORD) + 1 - (y >> ADDRESS_BITS_PER_WORD)) * width;
        if (wcost > hcost) {
            for (int i = x, max = x + width; i < max; i++) {
                if (this.any(this.dataH, y, i, height, this.hwl)) {
                    return true;
                }
            }
        } else {
            for (int i = y, max = y + height; i < max; i++) {
                if (this.any(this.dataW, x, i, width, this.wwl)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 線形が全て黒色かを調べる
    private boolean all(long[] data, int a, int b, int size, int wl) {
        int startIdx = (a >> ADDRESS_BITS_PER_WORD) + b * wl;
        int endIdx = ((a + size - 1) >> ADDRESS_BITS_PER_WORD) + b * wl;

        if (data.length <= endIdx)
            return false;

        long mask;
        if (startIdx == endIdx) {
            if (a == 0) {
                mask = (1L << size) - 1;
            } else {
                mask = ((1L << a + size) - 1) ^ (1L << a) - 1;
            }
            return (data[startIdx] & mask) == mask;
        } else {
            for (int i = startIdx; i <= endIdx; i++) {
                if (i == startIdx && a != 0) {
                    mask = -1 ^ (1L << a) - 1;
                    if ((data[i] & mask) != mask)
                        return false;
                } else if (i == endIdx) {
                    mask = (1L << a + size) - 1;
                    if ((data[i] & mask) != mask)
                        return false;
                } else {
                    if (data[i] != -1L)
                        return false;
                }
            }
        }
        return true;
    }

    // 線形に黒色が含まれるかを調べる
    private boolean any(long[] data, int a, int b, int size, int wl) {
        int startIdx = (a >> ADDRESS_BITS_PER_WORD) + b * wl;
        int endIdx = ((a + size - 1) >> ADDRESS_BITS_PER_WORD) + b * wl;

        if (data.length <= endIdx)
            return false;

        long mask;
        if (startIdx == endIdx) {
            if (a == 0) {
                mask = (1L << size) - 1;
            } else {
                mask = ((1L << a + size) - 1) ^ (1L << a) - 1;
            }
            return (data[startIdx] & mask) != 0;
        } else {
            for (int i = startIdx; i <= endIdx; i++) {
                if (i == startIdx && a != 0) {
                    mask = -1 ^ (1L << a) - 1;
                    if ((data[i] & mask) != 0)
                        return true;
                } else if (i == endIdx) {
                    mask = (1L << a + size) - 1;
                    if ((data[i] & mask) != 0)
                        return true;
                } else {
                    if (data[i] != 0)
                        return true;
                }
            }
        }
        return false;
    }
}