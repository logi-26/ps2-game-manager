package ps2gm.game.manager;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Rescales a user-picked image file to the right dimensions for the
 * requested ART slot and writes it into the OPL ART directory
 */
public final class ImageArtProcessor {

    private ImageArtProcessor() {}

    // This re-sizes and copies the given image file into the OPL ART directory
    public static void applySelectedImage(File imageFile, String coverType, String gameName, String gameID){

        // Attempts to load, re-size and copy the image file to the OPL ART directory
        try {
            Image image = ImageIO.read(imageFile);

            // Re-scale the image (Different scales depending on the image)
            BufferedImage scaledImage = null;
            switch (coverType) {
                case "_ICO":
                    scaledImage = scaleImage(image, 64, 64);                                                      // Disc image
                    break;
                case "_BG":
                    scaledImage = scaleImage(image, 640, 480);                                                    // Background image
                    break;
                case "_SCR":
                case "_SCR2":
                    scaledImage = scaleImage(image, 250, 188);                                                    // Screenshot images
                    break;
                case "_COV":
                    if (PopsGameManager.getCurrentConsole() == Console.PS1) {scaledImage = scaleImage(image, 140, 140);}           // PS1 front cover image
                    else if (PopsGameManager.getCurrentConsole() == Console.PS2) {scaledImage = scaleImage(image, 140, 200);}      // PS2 front cover image
                    break;
                    case "_COV2":
                    if (PopsGameManager.getCurrentConsole() == Console.PS1) {scaledImage = scaleImage(image, 140, 140);}           // PS1 rear cover image
                    else if (PopsGameManager.getCurrentConsole() == Console.PS2) {scaledImage = scaleImage(image, 242, 344);}      // PS2 rear cover image
                    break;
                case "_LAB":
                case "_LGO":
                    scaledImage = (image instanceof BufferedImage) ? (BufferedImage) image : scaleImage(image, 256, 256);
                    break;
                default:
                    break;
            }

            // Get the file extension for the image file
            String imageExtension = null;
            int i = imageFile.getPath().lastIndexOf('.');
            if (i > 0) {imageExtension = imageFile.getPath().substring(i+1);}

            // Copy the re-scaled image file to the OPL ART directory
            if (imageExtension != null) {

                if (PopsGameManager.getCurrentConsole() == Console.PS1){

                    if (imageExtension.equals("png")) {

                        // If a jpg file with the same name already exists in the directory, delete it
                        File jpgFile = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverType + ".jpg");
                        if(jpgFile.exists() && !jpgFile.isDirectory()) {jpgFile.delete();}

                        ImageIO.write(scaledImage, "png",new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverType + ".png"));
                    }
                    else if (imageExtension.equals("jpg")) {

                        // If a png file with the same name already exists in the directory, delete it
                        File pngFile = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverType + ".png");
                        if(pngFile.exists() && !pngFile.isDirectory()) {pngFile.delete();}

                        ImageIO.write(scaledImage, "jpg",new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverType + ".jpg"));
                    }
                }
                else if (PopsGameManager.getCurrentConsole() == Console.PS2){

                    if (imageExtension.equals("png")) {

                        // If a jpg file with the same name already exists in the directory, delete it
                        File jpgFile = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverType + ".jpg");
                        if(jpgFile.exists() && !jpgFile.isDirectory()) {jpgFile.delete();}

                        ImageIO.write(scaledImage, "png",new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverType + ".png"));
                    }
                    else if (imageExtension.equals("jpg")) {

                        // If a png file with the same name already exists in the directory, delete it
                        File pngFile = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverType + ".png");
                        if(pngFile.exists() && !pngFile.isDirectory()) {pngFile.delete();}

                        ImageIO.write(scaledImage, "jpg",new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverType + ".jpg"));
                    }
                }
            }
        }
        catch (IOException ex) {PopsGameManager.displayErrorMessageDebug("Error processing the image file!\n\n" + ex.toString());}
    }

    // This scales an image file
    private static BufferedImage scaleImage(Image originalImage, int width, int height) {

        BufferedImage buffImage = null;

        try {
            buffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = (Graphics2D)buffImage.createGraphics();
            g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY));
            boolean b = g2d.drawImage(originalImage, 0, 0, width, height, null);
        }
        catch (Exception ex){PopsGameManager.displayErrorMessageDebug("Error scaling the image file!\n\n" + ex.toString());}

        return buffImage;
    }
}
