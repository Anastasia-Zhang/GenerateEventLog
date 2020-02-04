import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author  zhangxinyu
 * @date  2019/12/22 10:23
 * @version 1.0
 */


public class GenerateEventLog {
    static WFNet wfNet;
    static Set<String> traceSet = new TreeSet<>();

    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
        String modelFile = "E:\\研究生学习\\高级算法\\附件1\\Model1.pnml";
        String logFile = "E:\\研究生学习\\高级算法\\附件1\\Model1LogDemo.txt";
        getLogOfModel(modelFile, logFile);

    }

    /**
     * 生成业务过程模型日志
     * @param logFile 日志文件路径
     * @param modelFile 读取的PNML文件路径
     * */
    public static void getLogOfModel(String modelFile, String logFile) throws IOException, SAXException, ParserConfigurationException {
        wfNet = readPNMLFile(modelFile);
        String startPlace = wfNet.getStartPlace();
        ArrayList<String> placeIds = new ArrayList<>();
        placeIds.add(startPlace);
        traceSet = generateTrace(placeIds, "");
        System.out.println(traceSet.size());
//        for (String set : traceSet) {
//            System.out.println(set);
//        }
        writeLogFile(logFile);
    }

    /**
     * 读取PNML文件，生成过程日志
     * @param path PNML文件路径
     * @return WFNet 网
     * */
    private static WFNet readPNMLFile(String path) throws ParserConfigurationException, IOException, SAXException {
        WFNet wfNet = new WFNet();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        Document doc = docBuilder.parse(new File(path));

        // 得到库所列表将每个库所的id放入list中
        NodeList placeNodeList = doc.getElementsByTagName("place");
        for (int i = 0; i < placeNodeList.getLength(); i++) {
            Node placeNode = placeNodeList.item(i);
            wfNet.getPlacesList().add(placeNode.getAttributes().getNamedItem("id").getNodeValue());
        }

        // 得到变迁列表将每个变迁的id放入list中
        NodeList transitionNodeList = doc.getElementsByTagName("transition");
        for (int i = 0; i < transitionNodeList.getLength(); i++) {
            Node transitionNode = transitionNodeList.item(i);
            String transId = transitionNode.getAttributes().getNamedItem("id").getNodeValue();
            wfNet.getTransitionList().add(transId);

            // 得到变迁子节点列表
            NodeList tranChildNode = transitionNode.getChildNodes();
            for (int j = 0; j < tranChildNode.getLength(); j++) {
                Node childNode = tranChildNode.item(j);

                // 遍历子节点的二级结点得到变迁的name值 <name><text>p0</text></name>
                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    for (Node node = childNode.getFirstChild(); node != null; node = node.getNextSibling()) {
                        if (node.getNodeName().equals("text")) {
                            Node titleNode = node.getFirstChild();
                            // 将变迁的name存储到wfNet中的transitionMap中
                            wfNet.getTransitionMap().put(transId, titleNode.getNodeValue());
                        }
                    }
                }
            }
        }

        // 得到wf网关系矩阵
        int n = wfNet.getTransitionList().size(); // 变迁个数
        int m = wfNet.getPlacesList().size(); // 库所个数
        int[][] transitionInput = new int[n][m];
        int[][] transitionOutput = new int[n][m];
        NodeList arcNodeList = doc.getElementsByTagName("arc");
        for (int i = 0; i < arcNodeList.getLength(); i++) {
            Node arcNode = arcNodeList.item(i);
            String arcSource = arcNode.getAttributes().getNamedItem("source").getNodeValue();
            String arcTarget = arcNode.getAttributes().getNamedItem("target").getNodeValue();
            if (wfNet.getPlacesList().contains(arcSource)) {
                // （s，t）对应的弧，计算输入矩阵
                int inputCol = wfNet.getPlacesList().indexOf(arcSource);
                int inputRow = wfNet.getTransitionList().indexOf(arcTarget);
                transitionInput[inputRow][inputCol]++;
            } else if (wfNet.getTransitionList().contains(arcSource)) {
                // （t，s）对应的弧，计算输出矩阵
                int outputRow = wfNet.getTransitionList().indexOf(arcSource);
                int outputCol = wfNet.getPlacesList().indexOf(arcTarget);
                transitionOutput[outputRow][outputCol]++;
            }
        }
        wfNet.setTransitionInput(transitionInput);
        wfNet.setTransitionOutput(transitionOutput);
        ArrayList<Integer> counter = new ArrayList<>();

        //初始化计数器
        for (int i = 0; i < wfNet.getTransitionList().size(); i++) {
            counter.add(0);
        }
        wfNet.setTransCounter(counter);
        return wfNet;
    }

    /**
     * 依据过程模型生成日志
     * @param placeIds 填入令牌的库所列表，代表其指向的变迁可以执行
     * @param path 已经执行的变迁id列表，用变迁id判断是否重复
     * @return 该过程模型所有的过程日志路径
     * */
    private static Set<String> generateTrace(ArrayList<String> placeIds, String path) {

        // 判断是否为结束状态
        String endPlace = wfNet.getEndPlace();
        for (String placeId : placeIds) {
            if (placeId.equals(endPlace)) {
                traceSet.add(path);
                return traceSet;
            }
        }

        // 遍历库所列表
        for (int j = 0; j < placeIds.size(); j++) {
            // 找到库所指向的变迁
            String placeId = placeIds.get(j);
            ArrayList<String> toTransList = wfNet.getPlaceOut(placeId);

            // 判断是否有环
            boolean hasCircle = false;
            if (toTransList.size() >= 2) {
                hasCircle = true;
            }

            for (int i = 0; i < toTransList.size(); i++) {

                // 每个trans找到他的place
                String transId = toTransList.get(i);
                ArrayList<String> toPlacesList = wfNet.getTransitionOut(transId);
                String newPath = path + " " + transId;
                ArrayList<String> newPlaceIds = new ArrayList<>(placeIds);
                int transIndex = wfNet.getTransitionList().indexOf(transId);
                wfNet.addTransCount(transId);

                // 判断环执行的次数是否大于1
                if (hasCircle && wfNet.getTransCounter().get(transIndex) >= 2) {
                    wfNet.minusTransCount(transId);
                    continue;
                }
                // 如果变迁是并行结构的终点且其输入库所全都填入令牌
                if (isANDEndTransVisited(placeIds, transId)) {
                    generateTrace(toPlacesList, newPath); // 消耗掉所有的输入库所，在输出库所填入变迁（placesIds全部清空）
                }
                // 如果变迁是并行结构的终点，且有的库所没有被访问过
                else if (isBeforeEnd(placeId, placeIds, transId)) {
                    break;
                } else { // 顺序结构或者冲突结构
                    newPlaceIds.remove(placeId);
                    newPlaceIds.addAll(toPlacesList); // 只消耗当前的库所
                    generateTrace(newPlaceIds, newPath);
                }
                wfNet.minusTransCount(transId);
            }
        }
        return traceSet;
    }

    /**
     * 判断是否为并行结构的终点且都被填入令牌（当前访问过的参数列表的库所是否所有的出度都包含给定的transId）
     * @param placeIds 填入令牌的库所列表
     * @param transId 变迁id
     * @return 是否为变迁结构的终点且都填入令牌
     * */
    private static boolean isANDEndTransVisited(ArrayList<String> placeIds, String transId) {
        //ArrayList<String> transInputPlaceList = wfNet.getTransitionInput(transId);
        for (String placeId : placeIds) {
            ArrayList<String> placeOutTrans = wfNet.getPlaceOut(placeId);
            if (!placeOutTrans.contains(transId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是当前遍历的库所和变迁是否
     * @param placeIds 填入令牌的库所列表
     * @param transId 变迁id
     * @return 是否为变迁结构的终点且都填入令牌
     * */
    private static boolean isBeforeEnd(String currentPlaceId, ArrayList<String> placeIds, String transId) {
        ArrayList<String> transInputPlaceList = wfNet.getTransitionInput(transId);
        if (transInputPlaceList.size() >= 2) {
            for (String placeId : placeIds) {
                ArrayList<String> placeOutTrans = wfNet.getPlaceOut(placeId);
                if (placeOutTrans.contains(transId) && placeId.equals(currentPlaceId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 把变迁执行路径写入日志文件
     * @param logFile 要存储的日志文件路径
     * */
    private static void writeLogFile(String logFile) throws IOException {
        File file = new File(logFile);
        FileWriter fileWriter = new FileWriter(file);
        int id = 1;
        for (String trace : traceSet) {
            StringBuilder logTrace = new StringBuilder();
            String[] transIdList = trace.split(" ");
            // 得到变迁id对应的名称
            for (String transId : transIdList){
                if (transId.length() > 0){
                    String transValue = wfNet.getTransitionMap().get(transId);
                    logTrace.append(" ").append(transValue);
                }
            }
            fileWriter.write("case " + id + " : " + logTrace);
            fileWriter.write("\r\n");
            id ++;
        }
        fileWriter.close();
        //System.out.println("finish");
    }

}

class WFNet {
    private ArrayList<String> placesList;
    private ArrayList<String> transitionList;
    private HashMap<String, String> transitionMap;
    private int[][] transitionOutput;
    private int[][] transitionInput;
    private ArrayList<Integer> transCounter;

    public WFNet(ArrayList<String> placesList, ArrayList<String> transitionList,
                 HashMap<String, String> transitionMap, int[][] transitionOutput,
                 int[][] transitionInput, ArrayList<Integer> transCounter) {
        this.placesList = placesList;
        this.transitionList = transitionList;
        this.transitionMap = transitionMap;
        this.transitionOutput = transitionOutput;
        this.transitionInput = transitionInput;
        this.transCounter = transCounter;
    }

    WFNet() {
        this.placesList = new ArrayList<>();
        this.transitionList = new ArrayList<>();
        this.transitionMap = new HashMap<>();
        this.transitionInput = null;
        this.transitionOutput = null;
        this.transCounter = new ArrayList<>();
    }

    ArrayList<String> getPlacesList() {
        return placesList;
    }

    void setPlacesList(ArrayList<String> placesList) {
        this.placesList = placesList;
    }

    ArrayList<String> getTransitionList() {
        return transitionList;
    }

    void setTransitionList(ArrayList<String> transitionList) {
        this.transitionList = transitionList;
    }

    HashMap<String, String> getTransitionMap() {
        return transitionMap;
    }

    void setTransitionMap(HashMap<String, String> transitionMap) {
        this.transitionMap = transitionMap;
    }

    int[][] getTransitionOutput() {
        return transitionOutput;
    }

    void setTransitionOutput(int[][] transitionOutput) {
        this.transitionOutput = transitionOutput;
    }

    int[][] getTransitionInput() {
        return transitionInput;
    }

    void setTransitionInput(int[][] transitionInput) {
        this.transitionInput = transitionInput;
    }

    ArrayList<Integer> getTransCounter() {
        return transCounter;
    }

    void setTransCounter(ArrayList<Integer> transCounter) {
        this.transCounter = transCounter;
    }

    // 得到输入库所
    public String getStartPlace() {
        String placeId = "";
        for (int i = 0; i < placesList.size(); i++) {
            int sum = 0;
            for (int j = 0; j < transitionList.size(); j++) {
                sum += transitionOutput[j][i];
            }
            if (sum == 0) {
                placeId = placesList.get(i);
                break;
            }
        }
        return placeId;
    }

    // 得到输出库所
    public String getEndPlace() {
        String placeId = "";
        for (int i = 0; i < placesList.size(); i++) {
            int sum = 0;
            for (int j = 0; j < transitionList.size(); j++) {
                sum += transitionInput[j][i];
            }
            if (sum == 0) {
                placeId = placesList.get(i);
                break;
            }
        }
        return placeId;
    }

    // 得到变迁对应的输出库所
    public ArrayList<String> getTransitionOut(String transitionId) {
        int index = transitionList.indexOf(transitionId);
        ArrayList<String> transToPlaceList = new ArrayList<>();
        for (int i = 0; i < placesList.size(); i++) {
            if (transitionOutput[index][i] == 1) {
                transToPlaceList.add(placesList.get(i));
            }
        }
        return transToPlaceList;
    }

    // 得到库所输出变迁
    public ArrayList<String> getPlaceOut(String placeId) {
        int index = placesList.indexOf(placeId);
        ArrayList<String> placeToTransList = new ArrayList<>();
        for (int i = 0; i < transitionList.size(); i++) {
            if (transitionInput[i][index] == 1) {
                placeToTransList.add(transitionList.get(i));
            }
        }
        return placeToTransList;
    }

    // 得到能到达此变迁的所有库所列表
    public ArrayList<String> getTransitionInput(String transitionId) {
        int index = transitionList.indexOf(transitionId);
        ArrayList<String> transToPlaceList = new ArrayList<>();
        for (int i = 0; i < placesList.size(); i++) {
            if (transitionInput[index][i] == 1) {
                transToPlaceList.add(placesList.get(i));
            }
        }
        return transToPlaceList;
    }

    // 计数器加1
    public void addTransCount(String transId) {
        int value = transCounter.get(transitionList.indexOf(transId));
        transCounter.set(transitionList.indexOf(transId), ++value);
    }

    // 计数器减1
    public void minusTransCount(String transId) {
        int value = transCounter.get(transitionList.indexOf(transId));
        transCounter.set(transitionList.indexOf(transId), --value);
    }

}
