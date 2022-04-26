package re.heartdroid.server;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import heart.*;
import heart.alsvfd.SimpleNumeric;
import heart.alsvfd.SimpleSymbolic;
import heart.exceptions.*;
import heart.parser.hmr.HMRParser;
import heart.parser.hmr.runtime.SourceFile;
import heart.uncertainty.CertaintyFactorsEvaluator;
import heart.xtt.Attribute;
import heart.xtt.Table;
import heart.xtt.Type;
import heart.xtt.XTTModel;
import re.heartdroid.cmd.ConflictSetMostCertainWin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

class HMRServerThread implements Runnable {

    private final Socket clientSocket;

    public static class JCommanderConfiguration {
        @Parameter(description = "<path to the HMR file>", required = true)
        private List<String> files = new LinkedList<String>();

        @Parameter(names = "-tabs", description = "Order of tables used in the inference", required = true,
                variableArity = true)
        private List<String> tabs = new LinkedList<String>();

        @DynamicParameter(names = "-A", description = "Initial values of attributes")
        private Map<String, String> attributes = new HashMap<String, String>();

        @Parameter(names = "--help", help = true, description = "Display help (this message)")
        public boolean help;
    }

    public HMRServerThread(Socket clientSocket){
        this.clientSocket = clientSocket;
    }


    public void run(){
        HMRServerThread.JCommanderConfiguration jcc = new HMRServerThread.JCommanderConfiguration();
        PrintWriter out = null;
        BufferedReader in = null;
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(
                    new InputStreamReader(
                            clientSocket.getInputStream()));
            String inputLine = in.readLine();
            System.out.println("Received "+inputLine);

            JCommander jcomm = new JCommander(jcc, inputLine.split(" "));


            if (jcc.help) {
                jcomm.usage();
                return;
            }

            SourceFile hmr_file = new SourceFile(jcc.files.get(0));
            HMRParser parser = new HMRParser();

            parser.parse(hmr_file);
            XTTModel model = parser.getModel();

            State initial  = new State();
            for (String attname : jcc.attributes.keySet()) {
                String strval = jcc.attributes.get(attname);
                Attribute att = model.getAttributeByName(attname);

                if (att == null) {
                    throw new Exception("Attribute: \"" + attname + "\" is not registered in model");
                }
                Type type = att.getType();
                if (type.isNumeric()) {
                    SimpleNumeric value = new SimpleNumeric(Double.valueOf(strval));
                    if (value.isInTheDomain(type)) {
                        initial.addStateElement(new StateElement(attname, value));
                    }
                }
                else if (type.isSymbolic()) {
                    SimpleSymbolic value = new SimpleSymbolic(strval);
                    initial.addStateElement(new StateElement(attname, value));
                }
            }

            Set<String> tableNames = new HashSet<String>();
            for (Table t : model.getTables()) {
                tableNames.add(t.getName());
            }

            for (String tname : jcc.tabs) {
                if (!tableNames.contains(tname)) {
                    throw new Exception("Table: " + tname + " is not registered in model");
                }
            }

            String[] tabs = new String[jcc.tabs.size()];
            tabs = jcc.tabs.toArray(tabs);
            Debug.debugLevel = Debug.Level.SILENT;
            HeaRT.fixedOrderInference(model, tabs,
                    new Configuration.Builder()
                            .setInitialState(initial)
                            .setCsr(new ConflictSetMostCertainWin()).setUte(new CertaintyFactorsEvaluator())//fires all rules from cs
                            .build());

            State current = HeaRT.getWm().getCurrentState(model);
            for(StateElement se : current){
               out.println("Attribute "+se.getAttributeName()+" = "+se.getValue());
            }

        } catch (ModelBuildingException e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        } catch (ParsingSyntaxException e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        } catch (NotInTheDomainException e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        } catch (BuilderException e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        } catch (AttributeNotRegisteredException e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            out.println("ERROR: "+e.getMessage());
            e.printStackTrace();
        }finally {
            if(out != null) {
                out.close();
            }
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    out.println("ERROR: "+e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


}

public class HMRServer{
        public static void main(String[] args) throws IOException {
            if (args.length < 2) {
                System.out.println("Not enough parameters to run a server. Usage: java server.jar <port> <n_jobs>");
            }
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Integer.parseInt(args[1]));
            ServerSocket serverSocket = null;
            try {
                System.out.println("Listening on port "+args[0]);
                serverSocket = new ServerSocket(Integer.parseInt(args[0]));
            } catch (IOException e) {
                System.out.println("Could not listen on port: 6666");
                System.exit(-1);
            }

            while(true) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    System.out.println("Connected...");
                    executor.execute(new HMRServerThread(clientSocket));
                } catch (IOException e) {
                    System.out.println("Accept failed: 6666");
                    System.exit(-1);
                }
            }
        }
}
