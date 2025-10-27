/* ATMApp.java
   Simple ATM Management System (Swing) with Customer + Technician modules.
   File-based storage: accounts.txt (id,pin,name,balance) and transactions.txt (log)
   Compile: javac ATMApp.java
   Run:     java ATMApp
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ATMApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                AccountManager.getInstance().loadAccounts(); // ensure accounts file exists
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ATMGUI();
        });
    }

    // --------------------------- Account class ---------------------------
    static class Account {
        String id;
        String pin;
        String name;
        double balance;

        Account(String id, String pin, String name, double balance) {
            this.id = id;
            this.pin = pin;
            this.name = name;
            this.balance = balance;
        }

        synchronized boolean withdraw(double amt) {
            if (amt <= 0) return false;
            if (balance >= amt) {
                balance -= amt;
                return true;
            } else {
                return false;
            }
        }

        synchronized void deposit(double amt) {
            if (amt > 0) balance += amt;
        }

        synchronized boolean transferTo(Account other, double amt) {
            if (amt <= 0) return false;
            if (balance >= amt) {
                balance -= amt;
                other.deposit(amt);
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return id + "," + pin + "," + name.replace(",", "") + "," + String.format("%.2f", balance);
        }
    }

    // ------------------------ AccountManager (file-based) ------------------------
    static class AccountManager {
        private static final AccountManager instance = new AccountManager();
        private final Map<String, Account> accounts = new HashMap<>();
        private final File accountsFile = new File("accounts.txt");
        private final File transactionsFile = new File("transactions.txt");
        private double atmCash = 10000.00; // ATM cash pool (tech can refill)

        private AccountManager() {}

        public static AccountManager getInstance() {
            return instance;
        }

        public synchronized void loadAccounts() throws IOException {
            if (!accountsFile.exists()) {
                // create sample accounts
                accountsFile.createNewFile();
                try (PrintWriter pw = new PrintWriter(new FileWriter(accountsFile))) {
                    pw.println("1001,1234,vignesh reddy,15000.00");
                    pw.println("1002,2345,tripuresh,30000.00");
                    pw.println("1003,3456,yuva kishore,10000.00");
                }
            }
            accounts.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(accountsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] p = line.split(",", 4);
                    if (p.length < 4) continue;
                    String id = p[0].trim();
                    String pin = p[1].trim();
                    String name = p[2].trim();
                    double bal = 0.0;
                    try { bal = Double.parseDouble(p[3].trim()); } catch (Exception ex) {}
                    accounts.put(id, new Account(id, pin, name, bal));
                }
            }
            if (!transactionsFile.exists()) transactionsFile.createNewFile();
        }

        public synchronized void saveAccounts() throws IOException {
            try (PrintWriter pw = new PrintWriter(new FileWriter(accountsFile,false))) {
                for (Account a : accounts.values()) {
                    pw.println(a.toString());
                }
            }
        }

        public synchronized Account authenticate(String id, String pin) {
            Account a = accounts.get(id);
            if (a != null && a.pin.equals(pin)) return a;
            return null;
        }

        public synchronized Account getAccount(String id) {
            return accounts.get(id);
        }

        public synchronized boolean addAccount(String id, String pin, String name, double balance) throws IOException {
            if (accounts.containsKey(id)) return false;
            Account a = new Account(id, pin, name, balance);
            accounts.put(id, a);
            saveAccounts();
            logTransaction("ADMIN", "ADD_ACCOUNT", id, balance);
            return true;
        }

        public synchronized boolean removeAccount(String id) throws IOException {
            if (!accounts.containsKey(id)) return false;
            accounts.remove(id);
            saveAccounts();
            logTransaction("ADMIN", "REMOVE_ACCOUNT", id, 0.0);
            return true;
        }

        public synchronized void persistAndLog(Account acc, String type, double amt) throws IOException {
            saveAccounts();
            logTransaction(acc.id, type, acc.id, amt);
        }

        private synchronized void logTransaction(String who, String type, String targetId, double amt) throws IOException {
            try (PrintWriter pw = new PrintWriter(new FileWriter(transactionsFile, true))) {
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                pw.printf("%s | %s | %s | target:%s | %.2f%n", ts, who, type, targetId, amt);
            }
        }

        public synchronized List<String> readAllTransactions() throws IOException {
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(transactionsFile))) {
                String l;
                while ((l = br.readLine()) != null) lines.add(l);
            }
            return lines;
        }

        public synchronized double getAtmCash() {
            return atmCash;
        }

        public synchronized void refillAtmCash(double amt) throws IOException {
            if (amt > 0) {
                atmCash += amt;
                logTransaction("TECH", "REFILL_ATM", "ATM", amt);
            }
        }

        public synchronized boolean withdrawFromAtm(double amt) {
            if (atmCash >= amt) {
                atmCash -= amt;
                return true;
            }
            return false;
        }

        // for demo/testing: expose a snapshot list of accounts
        public synchronized List<Account> listAccounts() {
            return new ArrayList<>(accounts.values());
        }
    }

    // --------------------------- GUI ---------------------------
    static class ATMGUI extends JFrame {
        private CardLayout cards = new CardLayout();
        private JPanel mainPanel = new JPanel(cards);

        // login components
        private JTextField idField = new JTextField(12);
        private JPasswordField pinField = new JPasswordField(12);

        // after login
        private Account currentAccount = null;

        ATMGUI() {
            setTitle("ATM Management System");
            setSize(600, 420);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            mainPanel.add(buildWelcomePanel(), "welcome");
            mainPanel.add(buildCustomerPanel(), "customer");
            mainPanel.add(buildTechnicianPanel(), "tech");
            mainPanel.add(buildAdminAccountListPanel(), "acctlist");
            add(mainPanel);
            cards.show(mainPanel, "welcome");
            setVisible(true);
        }

        private JPanel buildWelcomePanel() {
            JPanel p = new JPanel(new BorderLayout());
            JPanel center = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(8,8,8,8);
            c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
            JLabel h = new JLabel("ATM Management System", SwingConstants.CENTER);
            h.setFont(new Font("SansSerif", Font.BOLD, 20));
            center.add(h, c);

            c.gridwidth = 1;
            c.gridy++;
            center.add(new JLabel("User ID:"), c);
            c.gridx = 1;
            center.add(idField, c);

            c.gridx = 0; c.gridy++;
            center.add(new JLabel("PIN:"), c);
            c.gridx = 1;
            center.add(pinField, c);

            c.gridx = 0; c.gridy++;
            JButton loginCustomer = new JButton("Customer Login");
            center.add(loginCustomer, c);
            c.gridx = 1;
            JButton loginTech = new JButton("Technician Login");
            center.add(loginTech, c);

            loginCustomer.addActionListener(e -> handleCustomerLogin());
            loginTech.addActionListener(e -> handleTechLogin());

            // quick-help panel below
            JPanel bottom = new JPanel();
            bottom.add(new JLabel(""));
            p.add(center, BorderLayout.CENTER);
            p.add(bottom, BorderLayout.SOUTH);
            return p;
        }

        private void handleCustomerLogin() {
            String id = idField.getText().trim();
            String pin = new String(pinField.getPassword()).trim();
            if (id.isEmpty() || pin.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter both ID and PIN.");
                return;
            }
            Account a = AccountManager.getInstance().authenticate(id, pin);
            if (a == null) {
                JOptionPane.showMessageDialog(this, "Invalid credentials.");
                return;
            }
            currentAccount = a;
            showCustomerMain();
        }

        private void handleTechLogin() {
            // For demo: technician uses hardcoded credentials: tech / 0000
            String id = idField.getText().trim();
            String pin = new String(pinField.getPassword()).trim();
            if (id.equalsIgnoreCase("tech") && pin.equals("0000")) {
                cards.show(mainPanel, "tech");
                clearLoginFields();
            } else {
                JOptionPane.showMessageDialog(this, "Technician login requires ID=tech and PIN=0000 (demo).");
            }
        }

        private void clearLoginFields() {
            idField.setText("");
            pinField.setText("");
        }

        // ---------------- Customer panel ----------------
        private JPanel customerMainPanel;
        private JLabel lblWelcome = new JLabel();
        private JLabel lblBalance = new JLabel();

        private JPanel buildCustomerPanel() {
            customerMainPanel = new JPanel(new BorderLayout());
            JPanel top = new JPanel(new GridLayout(2,1));
            top.add(lblWelcome);
            top.add(lblBalance);
            customerMainPanel.add(top, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(5,1,8,8));
            JButton btnCheck = new JButton("Check Balance");
            JButton btnWithdraw = new JButton("Withdraw");
            JButton btnDeposit = new JButton("Deposit");
            JButton btnTransfer = new JButton("Transfer");
            JButton btnLogout = new JButton("Logout");

            center.add(btnCheck);
            center.add(btnWithdraw);
            center.add(btnDeposit);
            center.add(btnTransfer);
            center.add(btnLogout);

            btnCheck.addActionListener(e -> doCheckBalance());
            btnWithdraw.addActionListener(e -> doWithdraw());
            btnDeposit.addActionListener(e -> doDeposit());
            btnTransfer.addActionListener(e -> doTransfer());
            btnLogout.addActionListener(e -> { currentAccount=null; cards.show(mainPanel,"welcome"); });

            customerMainPanel.add(center, BorderLayout.CENTER);
            return customerMainPanel;
        }

        private void showCustomerMain() {
            lblWelcome.setText("Welcome, " + currentAccount.name + " (ID " + currentAccount.id + ")");
            lblBalance.setText("Balance: ₹ " + String.format("%.2f", currentAccount.balance));
            cards.show(mainPanel, "customer");
            clearLoginFields();
        }

        private void doCheckBalance() {
            lblBalance.setText("Balance: ₹ " + String.format("%.2f", currentAccount.balance));
            JOptionPane.showMessageDialog(this, "Current balance: ₹ " + String.format("%.2f", currentAccount.balance));
        }

        private void doWithdraw() {
            String s = JOptionPane.showInputDialog(this, "Enter amount to withdraw:");
            if (s == null) return;
            double amt = parseAmount(s);
            if (amt <= 0) { JOptionPane.showMessageDialog(this, "Enter a valid positive amount."); return; }
            AccountManager am = AccountManager.getInstance();
            synchronized (am) {
                if (!am.withdrawFromAtm(amt)) {
                    JOptionPane.showMessageDialog(this, "ATM doesn't have enough cash. Try smaller amount or contact technician.");
                    return;
                }
                boolean ok = currentAccount.withdraw(amt);
                if (!ok) {
                    // rollback atm cash
                    try { am.refillAtmCash(amt); } catch (IOException ex) {}
                    JOptionPane.showMessageDialog(this, "Insufficient account balance.");
                    return;
                }
                try { am.persistAndLog(currentAccount, "WITHDRAW", amt); } catch (IOException ex) { ex.printStackTrace(); }
            }
            lblBalance.setText("Balance: ₹ " + String.format("%.2f", currentAccount.balance));
            JOptionPane.showMessageDialog(this, "Withdrawal successful. Dispensed: ₹ " + String.format("%.2f", amt));
        }

        private void doDeposit() {
            String s = JOptionPane.showInputDialog(this, "Enter amount to deposit:");
            if (s == null) return;
            double amt = parseAmount(s);
            if (amt <= 0) { JOptionPane.showMessageDialog(this, "Enter a valid positive amount."); return; }
            currentAccount.deposit(amt);
            try { AccountManager.getInstance().persistAndLog(currentAccount, "DEPOSIT", amt); } catch (IOException ex) { ex.printStackTrace(); }
            lblBalance.setText("Balance: ₹ " + String.format("%.2f", currentAccount.balance));
            JOptionPane.showMessageDialog(this, "Deposit successful. ₹ " + String.format("%.2f", amt) + " added.");
        }

        private void doTransfer() {
            JPanel panel = new JPanel(new GridLayout(3,2));
            JTextField toId = new JTextField();
            JTextField amtField = new JTextField();
            panel.add(new JLabel("Recipient ID:"));
            panel.add(toId);
            panel.add(new JLabel("Amount:"));
            panel.add(amtField);

            int res = JOptionPane.showConfirmDialog(this, panel, "Transfer", JOptionPane.OK_CANCEL_OPTION);
            if (res != JOptionPane.OK_OPTION) return;

            String to = toId.getText().trim();
            double amt = parseAmount(amtField.getText().trim());
            if (to.isEmpty() || amt <= 0) { JOptionPane.showMessageDialog(this, "Invalid recipient or amount."); return; }
            AccountManager am = AccountManager.getInstance();
            Account target = am.getAccount(to);
            if (target == null) { JOptionPane.showMessageDialog(this, "Recipient not found."); return; }
            synchronized (am) {
                boolean ok = currentAccount.transferTo(target, amt);
                if (!ok) {
                    JOptionPane.showMessageDialog(this, "Insufficient balance or invalid amount.");
                    return;
                }
                try {
                    am.saveAccounts();
                    am.logTransaction(currentAccount.id, "TRANSFER", target.id, amt);
                } catch (IOException ex) { ex.printStackTrace(); }
            }
            lblBalance.setText("Balance: ₹ " + String.format("%.2f", currentAccount.balance));
            JOptionPane.showMessageDialog(this, "Transferred ₹ " + String.format("%.2f", amt) + " to " + target.name);
        }

        private double parseAmount(String s) {
            if (s == null) return -1;
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception e) { return -1; }
        }

        // ---------------- Technician panel ----------------
        private JPanel techPanel;
        private JTextArea techOutput = new JTextArea(12,40);

        private JPanel buildTechnicianPanel() {
            techPanel = new JPanel(new BorderLayout());
            JPanel top = new JPanel();
            top.add(new JLabel("Technician Console (demo credentials: ID=tech PIN=0000)"));
            techPanel.add(top, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(2,1));
            JPanel buttons = new JPanel(new GridLayout(1,6,8,8));
            JButton btnViewTrans = new JButton("View Transactions");
            JButton btnRefill = new JButton("Refill ATM Cash");
            JButton btnViewAccounts = new JButton("View Accounts");
            JButton btnAddAccount = new JButton("Add Account");
            JButton btnRemoveAccount = new JButton("Remove Account");
            JButton btnBack = new JButton("Logout");

            buttons.add(btnViewTrans);
            buttons.add(btnViewAccounts);
            buttons.add(btnAddAccount);
            buttons.add(btnRemoveAccount);
            buttons.add(btnRefill);
            buttons.add(btnBack);

            center.add(buttons);
            techOutput.setEditable(false);
            JScrollPane sp = new JScrollPane(techOutput);
            center.add(sp);
            techPanel.add(center, BorderLayout.CENTER);

            btnViewTrans.addActionListener(e -> doViewTransactions());
            btnRefill.addActionListener(e -> doRefillCash());
            btnAddAccount.addActionListener(e -> doAddAccount());
            btnRemoveAccount.addActionListener(e -> doRemoveAccount());
            btnViewAccounts.addActionListener(e -> doViewAccounts());
            btnBack.addActionListener(e -> { cards.show(mainPanel, "welcome"); });

            return techPanel;
        }

        private void doViewTransactions() {
            try {
                List<String> lines = AccountManager.getInstance().readAllTransactions();
                techOutput.setText("");
                for (String l : lines) techOutput.append(l + "\n");
            } catch (IOException ex) {
                techOutput.setText("Failed to read transactions: " + ex.getMessage());
            }
        }

        private void doRefillCash() {
            String s = JOptionPane.showInputDialog(this, "Enter amount to add to ATM cash:");
            if (s == null) return;
            double amt = parseAmount(s);
            if (amt <= 0) { JOptionPane.showMessageDialog(this, "Enter a positive amount."); return; }
            try {
                AccountManager.getInstance().refillAtmCash(amt);
                techOutput.append("Refilled ATM by ₹ " + String.format("%.2f", amt) + ". ATM cash: ₹ " + String.format("%.2f", AccountManager.getInstance().getAtmCash()) + "\n");
            } catch (IOException ex) { ex.printStackTrace(); }
        }

        private void doAddAccount() {
            JPanel p = new JPanel(new GridLayout(4,2));
            JTextField id = new JTextField();
            JTextField pin = new JTextField();
            JTextField name = new JTextField();
            JTextField bal = new JTextField("0.00");
            p.add(new JLabel("ID:")); p.add(id);
            p.add(new JLabel("PIN:")); p.add(pin);
            p.add(new JLabel("Name:")); p.add(name);
            p.add(new JLabel("Initial Balance:")); p.add(bal);
            int r = JOptionPane.showConfirmDialog(this, p, "Add Account", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                double b = parseAmount(bal.getText().trim());
                boolean ok = AccountManager.getInstance().addAccount(id.getText().trim(), pin.getText().trim(), name.getText().trim(), b);
                if (ok) techOutput.append("Added account " + id.getText().trim() + "\n"); else techOutput.append("Account exists or invalid\n");
            } catch (IOException ex) { ex.printStackTrace(); }
        }

        private void doRemoveAccount() {
            String id = JOptionPane.showInputDialog(this, "Enter account ID to remove:");
            if (id == null) return;
            try {
                boolean ok = AccountManager.getInstance().removeAccount(id.trim());
                if (ok) techOutput.append("Removed account " + id + "\n"); else techOutput.append("Account not found\n");
            } catch (IOException ex) { ex.printStackTrace(); }
        }

        private void doViewAccounts() {
            List<Account> list = AccountManager.getInstance().listAccounts();
            techOutput.setText("");
            techOutput.append("ATM cash pool: ₹ " + String.format("%.2f", AccountManager.getInstance().getAtmCash()) + "\n");
            for (Account a : list) {
                techOutput.append(String.format("ID:%s | Name:%s | Balance:₹ %.2f%n", a.id, a.name, a.balance));
            }
        }

        // An admin view that could be used later
        private JPanel buildAdminAccountListPanel() {
            JPanel p = new JPanel(new BorderLayout());
            p.add(new JLabel("Account list (admin)"), BorderLayout.NORTH);
            return p;
        }
    }
}