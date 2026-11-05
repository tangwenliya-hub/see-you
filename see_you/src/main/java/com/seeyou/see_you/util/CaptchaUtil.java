package com.seeyou.see_you.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;

/**
 * 服务端图形验证码：生成 4 位字符 + PNG（base64 data url）
 */
public class CaptchaUtil {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆 I O 0 1
    private static final int WIDTH = 150;
    private static final int HEIGHT = 44;

    /**
     * 验证码随机数发生器。
     * 原来是 java.util.Random —— 它是可预测的：拿到几个历史验证码就能推出后续序列，
     * 攻击者可以据此绕过（本项目还有 5 次失败锁定，但验证码本身不该留这个口子）。
     */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /** 随机 4 位验证码文本 */
    public static String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(SECURE_RANDOM.nextInt(CHARS.length())));
        return sb.toString();
    }

    public static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 生成验证码图片（data:image/png;base64,xxx） */
    public static String render(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // 干扰线/点只影响观感，用同一个安全随机数即可（无需可重现）
        java.util.Random r = SECURE_RANDOM;

        // 抗锯齿，字符更清晰
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(240, 248, 244));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线（少量、浅色，不压字符）
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(190 + r.nextInt(40), 215 + r.nextInt(30), 200 + r.nextInt(40)));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(r.nextInt(WIDTH), r.nextInt(HEIGHT), r.nextInt(WIDTH), r.nextInt(HEIGHT));
        }

        // 干扰点（少量）
        for (int i = 0; i < 15; i++) {
            g.setColor(new Color(200 + r.nextInt(40), 225 + r.nextInt(25), 210 + r.nextInt(35)));
            g.fillOval(r.nextInt(WIDTH), r.nextInt(HEIGHT), 2, 2);
        }

        // 字符：4 个字符均匀分布，旋转角度收小，保证完整可读
        int charW = (WIDTH - 20) / code.length(); // 每个字符的槽位宽度
        for (int i = 0; i < code.length(); i++) {
            int fontSize = 24 + r.nextInt(3);
            g.setFont(new Font("Georgia", Font.BOLD, fontSize));
            g.setColor(new Color(60 + r.nextInt(60), 110 + r.nextInt(60), 90 + r.nextInt(50)));
            double theta = (r.nextDouble() - 0.5) * 0.24; // ±0.12 弧度，不裁字
            int x = 10 + i * charW + (charW - fontSize) / 2;
            int y = (HEIGHT + fontSize) / 2 - 4;
            g.translate(x, y);
            g.rotate(theta);
            g.drawString(String.valueOf(code.charAt(i)), 0, 0);
            g.rotate(-theta);
            g.translate(-x, -y);
        }
        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
