/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.embeddingcurator;

import static cn.zhuatech.embeddingcurator.Checks.*;

import java.util.*;

/**
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 对已有向量计算余弦相似度，按代表样本及组内两两阈值归组，给出保留建议和相似度证据。
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Algorithm {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static double dot(double[] a, double[] b) {
    double s = 0;
    for (int i = 0; i < a.length; i++) s += a[i] * b[i];
    return Math.max(-1, Math.min(1, s));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> compute(Map<String, Object> cfg, List<Map<String, Object>> input) {
    unique(input);
    double threshold = num(cfg, "threshold", 0, 1);
    var rows = input.stream().sorted(Comparator.comparing(r -> (String) r.get("id"))).toList();
    var vec = new ArrayList<double[]>();
    var groups = new ArrayList<List<Integer>>();
    var out = new ArrayList<>();
    int dim = -1;
    for (var r : rows) {
      Object raw = r.get("vector");
      must(raw instanceof List<?>, "vector 应为数组");
      var v = (List<?>) raw;
      must(!v.isEmpty() && v.size() <= 4096 && (dim < 0 || dim == v.size()), "向量维度不一致或超出 1–4096");
      dim = v.size();
      double[] a = new double[dim];
      double scale = 0;
      for (int j = 0; j < dim; j++) {
        Object x = v.get(j);
        must(x instanceof Number, "向量包含非数字");
        a[j] = ((Number) x).doubleValue();
        must(Double.isFinite(a[j]) && Math.abs(a[j]) <= 1e12, "向量分量非有限或过大");
        scale = Math.max(scale, Math.abs(a[j]));
      }
      must(scale > 0, "零向量无法比较");
      double norm = 0;
      for (int j = 0; j < dim; j++) {
        a[j] /= scale;
        norm = Math.hypot(norm, a[j]);
      }
      for (int j = 0; j < dim; j++) a[j] /= norm;
      vec.add(a);
    }
    for (int i = 0; i < rows.size(); i++) {
      List<Integer> chosen = null;
      double best = -2;
      for (var g : groups) {
        double min = 1;
        for (int j : g) min = Math.min(min, dot(vec.get(i), vec.get(j)));
        if (min + 1e-12 >= threshold && min > best) {
          chosen = g;
          best = min;
        }
      }
      boolean keep = chosen == null;
      if (keep) {
        chosen = new ArrayList<>();
        groups.add(chosen);
        best = 1;
      }
      chosen.add(i);
      out.add(
          Map.of(
              "id",
              rows.get(i).get("id"),
              "representative",
              rows.get(chosen.getFirst()).get("id"),
              "action",
              keep ? "KEEP" : "REVIEW_DUPLICATE",
              "minSimilarity",
              best, "representativeSimilarity", keep ? 1.0 : dot(vec.get(i), vec.get(chosen.getFirst()))));
    }
    return result(
        Map.of(
            "vectors",
            rows.size(),
            "dimensions",
            dim,
            "groups",
            groups.size(),
            "candidates",
            rows.size() - groups.size()),
        out);
  }
}
