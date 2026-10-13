FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.5
# --batch 跳过安装确认；该镜像源与 docs/elasticsearch-setup.md 保持一致
RUN bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/9.4.5