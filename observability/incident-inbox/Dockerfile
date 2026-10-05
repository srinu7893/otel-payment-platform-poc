FROM python:3.13.7-slim
WORKDIR /app
COPY server.py /app/server.py
RUN mkdir /data && chown 10001:10001 /data
USER 10001:10001
CMD ["python3", "server.py"]
