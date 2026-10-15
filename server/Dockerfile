FROM node:22-alpine
WORKDIR /app
COPY package.json index.js economy.js shop.json ./
RUN mkdir -p /app/data && chown node:node /app/data
ENV PORT=8080 VERIFY=1 DATA_FILE=/app/data/economy.json
# keep the coins when the container is replaced: docker run -v onix-data:/app/data …
VOLUME /app/data
EXPOSE 8080
USER node
CMD ["node", "index.js"]
