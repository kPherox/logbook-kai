package logbook.internal.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import logbook.internal.LoggerHolder;
import logbook.internal.ThreadManager;
import lombok.Getter;
import lombok.Setter;

/**
 * スクリーンショットに関係するメソッドを集めたクラス
 *
 */
class ScreenCapture {

    /** Jpeg品質 */
    private static final float QUALITY = 0.9f;

    /** ゲーム画面サイズ */
    private static Dimension[] sizes = {
            new Dimension(600, 360), //50%
            new Dimension(720, 432), //60%
            new Dimension(800, 480), //67%
            new Dimension(837, 502), //70%
            new Dimension(840, 504), //70%
            new Dimension(900, 540), //75%
            new Dimension(960, 576), //80%
            new Dimension(1074, 645), //90%
            new Dimension(1080, 648), //90%
            new Dimension(1200, 720) //100%
    };

    @Setter
    @Getter
    private Robot robot;

    /** キャプチャ範囲 */
    @Setter
    @Getter
    private Rectangle rectangle;

    /** 切り取り範囲 */
    @Setter
    @Getter
    private Rectangle cutRect;

    private int size = 200;

    private ObservableList<ImageData> list;

    private ObjectProperty<ImageData> current;

    /** 切り取り範囲 */
    enum CutType {
        /** 切り取らない */
        NONE(null),
        /** 改装一覧の範囲(艦娘除く) */
        UNIT_WITHOUT_SHIP(new Rectangle(490, 154, 345, 547)),
        /** 改装一覧の範囲 */
        UNIT(new Rectangle(490, 154, 690, 547));

        private Rectangle angle;

        private CutType(Rectangle angle) {
            this.angle = angle;
        }

        Rectangle getAngle() {
            return this.angle;
        }
    }

    /**
     * スクリーンショット
     *
     * @param robot Robot
     * @param rectangle ゲーム画面の座標
     */
    ScreenCapture(Robot robot, Rectangle rectangle) {
        this.robot = robot;
        this.rectangle = rectangle;
    }

    void setItems(ObservableList<ImageData> list) {
        this.list = list;
    }

    void setCurrent(ObjectProperty<ImageData> current) {
        this.current = current;
    }

    void setSize(int size) {
        this.size = size;
    }

    void capture() throws IOException {
        ThreadManager.getExecutorService()
                .execute(this::execute);
    }

    void captureDirect(Path dir) {
        ThreadManager.getExecutorService()
                .execute(() -> this.executeDirect(dir));
    }

    private void execute() {
        try {
            ImageData image = new ImageData();
            image.setDateTime(ZonedDateTime.now());

            byte[] data;
            if (this.cutRect != null) {
                data = encodeJpeg(this.robot.createScreenCapture(this.rectangle), this.cutRect);
            } else {
                data = encodeJpeg(this.robot.createScreenCapture(this.rectangle));
            }
            image.setImage(data);

            Platform.runLater(() -> {
                this.current.set(image);
                this.list.add(image);
                while (this.list.size() > this.size) {
                    this.list.remove(0);
                }
            });
        } catch (IOException e) {
            LoggerHolder.get().warn("キャプチャ処理で例外", e);
        }
    }

    private void executeDirect(Path dir) {
        try {
            ImageData image = new ImageData();
            image.setDateTime(ZonedDateTime.now());

            byte[] data;
            if (this.cutRect != null) {
                data = encodeJpeg(this.robot.createScreenCapture(this.rectangle), this.cutRect);
            } else {
                data = encodeJpeg(this.robot.createScreenCapture(this.rectangle));
            }
            image.setImage(data);

            if (data != null) {
                Path to = dir.resolve(CaptureSaveController.DATE_FORMAT.format(ZonedDateTime.now()) + ".jpg");
                try (OutputStream out = Files.newOutputStream(to)) {
                    out.write(data);
                }
            }

            Platform.runLater(() -> {
                this.current.set(image);
            });
        } catch (IOException e) {
            LoggerHolder.get().warn("キャプチャ処理で例外", e);
        }
    }

    /**
     * 座標からスクリーンを取得します
     *
     * @param x X
     * @param y Y
     * @return スクリーン
     */
    static GraphicsConfiguration detectScreenDevice(int x, int y) {
        GraphicsDevice[] gds = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getScreenDevices();

        for (GraphicsDevice gd : gds) {
            for (GraphicsConfiguration gc : gd.getConfigurations()) {
                Rectangle r = gc.getBounds();
                if (r.contains(x, y)) {
                    return gc;
                }
            }
        }
        return null;
    }

    /**
     * イメージからゲーム画面を検索します
     *
     * @param image イメージ
     * @return 画面の座標
     */
    static Rectangle detectGameScreen(BufferedImage image) {
        BiImage biImage = new BiImage(image, Color.WHITE) {
            @Override
            protected boolean test(int a, int b) {
                return (a & 0xf0f0f0) == (b & 0xf0f0f0);
            }
        };
        for (int y = 0, height = image.getHeight() - sizes[0].height; y < height; y++) {
            for (int x = 0, width = image.getWidth() - sizes[0].width; x < width; x++) {
                for (int i = 0; i < sizes.length; i++) {
                    Dimension size = sizes[i];
                    if (!biImage.allW(x, y, size.width + 2))
                        break;
                    if (!biImage.allH(x, y, size.height + 2))
                        break;
                    if (!biImage.allW(x, y + size.height + 1, size.width + 2))
                        continue;
                    if (!biImage.allH(x + size.width + 1, y, size.height + 2))
                        continue;
                    if (biImage.allH(x + 1, y + 1, size.height))
                        continue;
                    if (biImage.allH(x + size.width, y + 1, size.height))
                        continue;
                    return new Rectangle(x + 1, y + 1, size.width, size.height);
                }
            }
        }
        return null;
    }

    /**
     * BufferedImageをJPEG形式にエンコードします
     *
     * @param image BufferedImage
     * @return JPEG形式の画像
     * @throws IOException 入出力例外
     */
    static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try {
                ImageWriteParam iwp = writer.getDefaultWriteParam();
                if (iwp.canWriteCompressed()) {
                    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    iwp.setCompressionQuality(QUALITY);
                }
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), iwp);
            } finally {
                writer.dispose();
            }
        }
        return out.toByteArray();
    }

    /**
     * BufferedImageをJPEG形式にエンコードします
     *
     * @param image BufferedImage
     * @param rect 画像の範囲
     * @return JPEG形式の画像
     * @throws IOException 入出力例外
     */
    static byte[] encodeJpeg(BufferedImage image, Rectangle rect) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try {
                ImageWriteParam iwp = writer.getDefaultWriteParam();
                if (iwp.canWriteCompressed()) {
                    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    iwp.setCompressionQuality(QUALITY);
                }
                writer.setOutput(ios);
                int x;
                int y;
                int w;
                int h;
                if (image.getWidth() == 1200 && image.getHeight() == 720) {
                    x = rect.x;
                    y = rect.y;
                    w = rect.width;
                    h = rect.height;
                } else {
                    x = (int) (rect.x * ((double) image.getWidth() / 1200));
                    y = (int) (rect.y * ((double) image.getHeight() / 720));
                    w = (int) (rect.width * ((double) image.getWidth() / 1200));
                    h = (int) (rect.height * ((double) image.getHeight() / 720));
                }
                writer.write(null, new IIOImage(image.getSubimage(x, y, w, h), null, null), iwp);
            } finally {
                writer.dispose();
            }
        }
        return out.toByteArray();
    }

    /**
     * 複数の画像を横に並べた画像を返します
     *
     * @param bytes JPEG形式などにエンコード済みの画像ファイルのバイト配列
     * @param column 列数
     * @return 横に並べた画像
     */
    static BufferedImage tileImage(List<byte[]> bytes, int column) throws IOException {
        if (bytes.isEmpty()) {
            return null;
        }
        BufferedImage base = ImageIO.read(new ByteArrayInputStream(bytes.get(0)));

        int baseWidth = base.getWidth();
        int baseHeight = base.getHeight();

        int width = baseWidth * Math.min(bytes.size(), column);
        int height = (int) (baseHeight * Math.ceil((float) bytes.size() / column));

        BufferedImage canvas = new BufferedImage(width, height, ColorSpace.TYPE_RGB);

        Graphics gc = canvas.createGraphics();
        gc.setColor(Color.WHITE);
        gc.fillRect(0, 0, width, height);
        for (int i = 0; i < bytes.size(); i++) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes.get(i)));
            int c = i % column;
            int r = (int) Math.ceil((float) (i + 1) / column) - 1;
            int x = baseWidth * c;
            int y = baseHeight * r;
            gc.drawImage(image, x, y, null);
        }
        return canvas;
    }

    /**
     * 画像データ
     *
     */
    static final class ImageData {

        /** 日付書式 */
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        /** 日付 */
        private ZonedDateTime dateTime;

        /** 画像データ */
        private Reference<byte[]> image;

        /**
         * 日付を取得します。
         * @return 日付
         */
        ZonedDateTime getDateTime() {
            return this.dateTime;
        }

        /**
         * 日付を設定します。
         * @param dateTime 日付
         */
        void setDateTime(ZonedDateTime dateTime) {
            this.dateTime = dateTime;
        }

        /**
         * 画像データを取得します。
         * @return 画像データ
         */
        byte[] getImage() {
            return this.image.get();
        }

        /**
         * 画像データを設定します。
         * @param image 画像データ
         */
        void setImage(byte[] image) {
            this.image = new SoftReference<>(image);
        }

        @Override
        public String toString() {
            return DATE_FORMAT.format(this.dateTime);
        }
    }

    /**
     * 2値画像
     */
    public static class BiImage {

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
}
