package ps2gm.game.manager;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;


public class GameLongNameScreen extends javax.swing.JDialog {

     ArrayList<Game> longNameList;
     String currentConsole;
     int selectedIndex;
    
    public GameLongNameScreen(java.awt.Frame parent, boolean modal, String console, ArrayList<Game> longNameList) {
        super(parent, modal);
        initComponents();
        this.longNameList = longNameList;
        this.currentConsole = console;
        
        createList();
        
        // Set the screen title
        this.setTitle("Long " + console + " Game Names");

        // Limit the number of chars in the text filed to 32
        jTextFieldGameNewTitle.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) { 
                if (jTextFieldGameNewTitle.getText().length() >= 32){e.consume();}
            }  
        });
    }
    
    
    // Convert the game list to an array and display in the GUI
    private void createList(){
        // Add the game names to the list box in the GUI
        List<String> gameNameList = new ArrayList<>();
        longNameList.stream().forEach((game) -> {gameNameList.add(game.getGameName());});
        createListGUI(gameNameList.toArray(new String[0]));
    }
    
    
    // This creates the list model for the games list in the GUI
    private void createListGUI(String[] games){
        
        // List model
        jListGameList.setModel(new javax.swing.AbstractListModel<String>() { 
            @Override
            public int getSize() {return games.length;}
            @Override
            public String getElementAt(int i) {return games[i];}
        });
        
        // Mouse listener
        jListGameList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt)){displayGameDetails();}
            }
        });
        
        // Key listener
        jListGameList.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            @Override
            public void keyPressed(KeyEvent e) {}
            @Override
            public void keyReleased(KeyEvent e) {displayGameDetails();}
        });
        
        jScrollPane1.setViewportView(jListGameList);
    }
    
    
    // This displays the game details
    private void displayGameDetails(){
        if (selectedIndex != -1){
            jTextFieldGameOldTitle.setText(longNameList.get(jListGameList.getSelectedIndex()).getGameName());
            jTextFieldGameNewTitle.setText(longNameList.get(jListGameList.getSelectedIndex()).getGameName().substring(0, 32)); 
        }
    }
    
    
    // Update the main game list and callback to the main GUI
    private void updateGameList(String console){
        longNameList.remove(selectedIndex);
        createList();

        if (console.equals("PS1")){
            // Re-generate the PS1 game list
            GameListManager.createGameListsPS1();

            // Generate a new conf_elm.cfg file
            GameListManager.writeConfigELM();
        }
        else if (console.equals("PS2")){
            
            // Re-generate the PS2 game list
            GameListManager.createGameListsPS2(false);
        }

        // Callback to update the main game list in the GUI
        PopsGameManager.callbackToUpdateGUIGameList(null, -1); 

        if (!longNameList.isEmpty()){
            jListGameList.setSelectedIndex(0);
            displayGameDetails();
        }
        else {
            jTextFieldGameOldTitle.setText("");
            jTextFieldGameNewTitle.setText("");
        }
    }
    
    
    // Rename the selected game (file work delegated to GameLongNameRenamer)
    private void renameGame(){

        String newTitle = jTextFieldGameNewTitle.getText();
        if (newTitle.equals("")) { return; }

        Game game = longNameList.get(selectedIndex);
        GameLongNameRenamer renamer = new GameLongNameRenamer(game, jTextFieldGameOldTitle.getText(), newTitle);
        String mode = PopsGameManager.getCurrentMode();

        if (currentConsole.equals("PS1")){

            for (Game g : GameListManager.getGameListPS1()){
                if (g.getGameName().equals(newTitle)){
                    JOptionPane.showMessageDialog(null,"A game with the same name is already in the game list!"," Game Rename Error",JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            switch (mode) {
                case "SMB":     if (renamer.renameLocalPS1("SB.")) { updateGameList("PS1"); } break;
                case "HDD_USB": if (renamer.renameLocalPS1("XX.")) { updateGameList("PS1"); } break;
                case "HDD":     renamer.ftpRenamePS1(); break;
            }
        }
        else if (currentConsole.equals("PS2")){

            for (Game g : GameListManager.getGameListPS2()){
                if (g.getGameName().equals(newTitle)){
                    JOptionPane.showMessageDialog(null,"A game with the same name is already in the game list!"," Game Rename Error",JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            switch (mode) {
                case "HDD_USB":
                case "SMB":
                    if (renamer.renameLocalPS2()) { updateGameList("PS2"); }
                    break;
                case "HDD":
                    JOptionPane.showMessageDialog(null,"The application cannot currently rename a PS2 game in HDD mode!"," Unable To Rename Game",JOptionPane.ERROR_MESSAGE);
                    break;
            }
        }
    }
 
   
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jListGameList = new javax.swing.JList<>();
        jPanel3 = new javax.swing.JPanel();
        jLabelGameTitle1 = new javax.swing.JLabel();
        jTextFieldGameNewTitle = new javax.swing.JTextField();
        jButtonRename = new javax.swing.JButton();
        jTextFieldGameOldTitle = new javax.swing.JTextField();
        jLabelGameTitle2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Title"));

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Game List"));

        jScrollPane1.setViewportView(jListGameList);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 373, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 233, Short.MAX_VALUE)
                .addContainerGap())
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Rename Game"));

        jLabelGameTitle1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabelGameTitle1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabelGameTitle1.setText("New Title:");
        jLabelGameTitle1.setToolTipText("");

        jTextFieldGameNewTitle.setPreferredSize(new java.awt.Dimension(402, 20));

        jButtonRename.setText("Rename");
        jButtonRename.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonRenameActionPerformed(evt);
            }
        });

        jTextFieldGameOldTitle.setEditable(false);
        jTextFieldGameOldTitle.setPreferredSize(new java.awt.Dimension(402, 20));

        jLabelGameTitle2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabelGameTitle2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabelGameTitle2.setText("Old Title:");
        jLabelGameTitle2.setToolTipText("");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabelGameTitle1, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextFieldGameNewTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabelGameTitle2, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextFieldGameOldTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButtonRename, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap(20, Short.MAX_VALUE)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelGameTitle2, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextFieldGameOldTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelGameTitle1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextFieldGameNewTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButtonRename, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // <editor-fold defaultstate="collapsed" desc="Button Click Evenets"> 
    private void jButtonRenameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonRenameActionPerformed
        
        if (jListGameList.getSelectedIndex() != -1){
            selectedIndex = jListGameList.getSelectedIndex();
            renameGame();
        }
    }//GEN-LAST:event_jButtonRenameActionPerformed
    // </editor-fold> 
    
    // <editor-fold defaultstate="collapsed" desc="Generated Variables">      
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonRename;
    private javax.swing.JLabel jLabelGameTitle1;
    private javax.swing.JLabel jLabelGameTitle2;
    private javax.swing.JList<String> jListGameList;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField jTextFieldGameNewTitle;
    private javax.swing.JTextField jTextFieldGameOldTitle;
    // End of variables declaration//GEN-END:variables
    // </editor-fold> 
}