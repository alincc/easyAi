package org.wlld.imageRecognition;


import org.wlld.MatrixTools.Matrix;
import org.wlld.MatrixTools.MatrixOperation;
import org.wlld.config.StudyPattern;
import org.wlld.imageRecognition.border.Border;
import org.wlld.imageRecognition.border.BorderBody;
import org.wlld.imageRecognition.border.Frame;
import org.wlld.imageRecognition.border.FrameBody;
import org.wlld.nerveEntity.SensoryNerve;
import org.wlld.tools.ArithUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Operation {//进行计算
    private TempleConfig templeConfig;//配置初始化参数模板
    private Convolution convolution = new Convolution();

    public Operation(TempleConfig templeConfig) {
        this.templeConfig = templeConfig;
    }

    public List<Double> convolution(Matrix matrix, Map<Integer, Double> tagging) throws Exception {
        //进行卷积
        int maxNub = 0;
        if (templeConfig.getRow() >= templeConfig.getColumn()) {
            maxNub = templeConfig.getRow();
        } else {
            maxNub = templeConfig.getColumn();
        }
        int id = -1;
        if (tagging != null && templeConfig.isHavePosition()) {
            for (Map.Entry<Integer, Double> entry : tagging.entrySet()) {
                if (entry.getValue() == 1) {
                    id = entry.getKey();
                    break;
                }
            }
        }
        Matrix matrix1 = convolution.getFeatures(matrix, maxNub, templeConfig, id);
        return sub(matrix1);
    }

    //模板学习
    public void study(Matrix matrix, Map<Integer, Double> tagging) throws Exception {
        if (templeConfig.getStudyPattern() == StudyPattern.Speed_Pattern) {
            List<Double> list = convolution(matrix, tagging);
            intoNerve(1, list, templeConfig.getSensoryNerves(), true, tagging);
        } else {
            throw new Exception("pattern is wrong");
        }
    }

    //卷积核学习
    public void learning(Matrix matrix, Map<Integer, Double> tagging, boolean isNerveStudy) throws Exception {
        if (templeConfig.getStudyPattern() == StudyPattern.Accuracy_Pattern) {
            Border border = null;
            if (templeConfig.isHavePosition() && isNerveStudy) {
                border = convolution.borderOnce(matrix, templeConfig);
            }
            boolean isKernelStudy = true;
            if (isNerveStudy) {
                isKernelStudy = false;
            }
            intoNerve2(1, matrix, templeConfig.getConvolutionNerveManager().getSensoryNerves(),
                    isKernelStudy, isNerveStudy, tagging, border);
        } else {
            throw new Exception("pattern is wrong");
        }
    }

    //图像视觉 speed 模式
    public void look(Matrix matrix, long eventId) throws Exception {
        if (templeConfig.getStudyPattern() == StudyPattern.Speed_Pattern) {
            List<Double> list = convolution(matrix, null);
            intoNerve(eventId, list, templeConfig.getSensoryNerves(), false, null);
        } else if (templeConfig.getStudyPattern() == StudyPattern.Accuracy_Pattern) {
            see(matrix, eventId);
        }
    }

    //边框检测+识别分类
    public void lookWithPosition(Matrix matrix, long eventId) throws Exception {
        Frame frame = templeConfig.getFrame();
        if (templeConfig.isHavePosition() && frame != null && frame.isReady()) {
            List<FrameBody> frameBodies = convolution.getRegion(matrix, frame);
            if (templeConfig.getStudyPattern() == StudyPattern.Speed_Pattern) {
                int maxNub = 0;
                if (templeConfig.getRow() >= templeConfig.getColumn()) {
                    maxNub = templeConfig.getRow();
                } else {
                    maxNub = templeConfig.getColumn();
                }
                ImageBack imageBack = templeConfig.getImageBack();
                for (FrameBody frameBody : frameBodies) {
                    //Speed 模式下的最后卷积结果
                    Matrix matrix1 = convolution.getFeatures(frameBody.getMatrix(), maxNub, templeConfig, -1);
                    frameBody.setEndMatrix(matrix1);
                    List<Double> list = sub(matrix1);
                    imageBack.setFrameBody(frameBody);
                    //进入神经网络判断
                    intoNerve(eventId, list, templeConfig.getSensoryNerves(), false, null);
                }
                toPositon(frameBodies, frame.getWidth(), frame.getHeight());
            } else if (templeConfig.getStudyPattern() == StudyPattern.Accuracy_Pattern) {

            }
        } else {
            throw new Exception("position not study or frame is not ready");
        }
    }

    private void toPositon(List<FrameBody> frameBodies, int width, int height) throws Exception {//把分类都拿出来
        for (FrameBody frameBody : frameBodies) {
            if (frameBody.getPoint() > templeConfig.getTh()) {//存在一个识别分类
                getBox(frameBody, width, height);
            }
        }
    }

    //获得预测边框
    private void getBox(FrameBody frameBody, int width, int height) throws Exception {
        if (templeConfig.isBoxReady()) {
            Matrix matrix = frameBody.getEndMatrix();
            int id = frameBody.getId();
            int x = frameBody.getX();
            int y = frameBody.getY();
            Map<Integer, BorderBody> borderBodyMap = templeConfig.getBorderBodyMap();
            BorderBody borderBody = borderBodyMap.get(id);
            //都是列向量，将参数转化为行向量最后再加1
            Matrix xw = borderBody.getxW();
            Matrix yw = borderBody.getyW();
            Matrix hw = borderBody.gethW();
            Matrix ww = borderBody.getwW();
            matrix = MatrixOperation.matrixToVector(matrix, true);
            //将参数矩阵的末尾填1
            matrix = MatrixOperation.push(matrix, 1, true);
            //锚点坐标及长宽预测值
            double tx = MatrixOperation.mulMatrix(matrix, xw).getNumber(0, 0);
            double ty = MatrixOperation.mulMatrix(matrix, yw).getNumber(0, 0);
            double th = MatrixOperation.mulMatrix(matrix, hw).getNumber(0, 0);
            double tw = MatrixOperation.mulMatrix(matrix, ww).getNumber(0, 0);
            //修正相对位置
            double realX = ArithUtil.add(ArithUtil.mul(tx, height), x);
        } else {
            throw new Exception("box is not study");
        }
    }

    //图像视觉 Accuracy 模式
    private void see(Matrix matrix, long eventId) throws Exception {
        if (templeConfig.getStudyPattern() == StudyPattern.Accuracy_Pattern) {
            intoNerve2(eventId, matrix, templeConfig.getConvolutionNerveManager().getSensoryNerves(),
                    false, false, null, null);
        } else {
            throw new Exception("pattern is wrong");
        }
    }

    private List<Double> sub(Matrix matrix) throws Exception {//
        List<Double> list = new ArrayList<>();
        int x = matrix.getX() - 1;
        int y = matrix.getY() - 1;
        for (int i = 0; i < templeConfig.getRow(); i++) {
            for (int j = 0; j < templeConfig.getColumn(); j++) {
                if (i > x || j > y) {
                    list.add(0.0);
                } else {
                    list.add(matrix.getNumber(i, j));
                }
            }
        }
        return list;
    }

    private void intoNerve(long eventId, List<Double> featurList, List<SensoryNerve> sensoryNerveList
            , boolean isStudy, Map<Integer, Double> map) throws Exception {
        for (int i = 0; i < sensoryNerveList.size(); i++) {
            sensoryNerveList.get(i).postMessage(eventId, featurList.get(i), isStudy, map);
        }
    }

    private void intoNerve2(long eventId, Matrix featur, List<SensoryNerve> sensoryNerveList
            , boolean isKernelStudy, boolean isNerveStudy
            , Map<Integer, Double> E, Border border) throws Exception {
        for (int i = 0; i < sensoryNerveList.size(); i++) {
            sensoryNerveList.get(i).postMatrixMessage(eventId, featur, isKernelStudy, isNerveStudy, E, border);
        }
    }
}