class Camera {
    constructor() {
        this.x = 0;
        this.y = 0;
    }

    update(target, canvas) {
        if (!target) return;
        this.x = target.x - canvas.width / 2;
        this.y = target.y - canvas.height / 2;
    }
}

class Renderer {
    constructor(ctx, canvas) {
        this.ctx = ctx;
        this.canvas = canvas;
        this.MAP_SIZE = 3000;
        this.GRID_SIZE = 100;

        this.ui = {
            playerCount: document.getElementById('player-count'),
            currentScore: document.getElementById('current-score'),
            leaderboardList: document.getElementById('leaderboard-list'),
            hud: document.getElementById('hud'),
            lb: document.getElementById('leaderboard')
        };
    }

    draw(gameState, camera, myId) {
        const { ctx, canvas } = this;

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        ctx.save();
        ctx.translate(-camera.x, -camera.y);

        this.drawGrid(camera);

        if (gameState.players) {
            const alivePlayers = gameState.players.filter(p => p.isAlive);

            alivePlayers.forEach(p => this.drawTerritory(p));
            alivePlayers.forEach(p => this.drawTrail(p));
            alivePlayers.forEach(p => this.drawHead(p));
        }

        ctx.restore();

        this.updateUI(gameState, myId);
    }

    drawTerritory(p) {
        if (!p.territory || p.territory.length === 0) return;

        const { ctx } = this;
        ctx.save();
        ctx.fillStyle = p.color;
        ctx.globalAlpha = 0.35;

        p.territory.forEach(polygon => {
            if (polygon.length < 3) return;

            ctx.beginPath();
            ctx.moveTo(polygon[0][0], polygon[0][1]);
            for (let i = 1; i < polygon.length; i++) {
                ctx.lineTo(polygon[i][0], polygon[i][1]);
            }
            ctx.closePath();
            ctx.fill();
        });
        ctx.restore();
    }

    drawTrail(p) {
        if (!p.trail || p.trail.length < 2) return;

        const { ctx } = this;
        ctx.save();
        ctx.strokeStyle = p.color;
        ctx.lineWidth = 5;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.globalAlpha = 0.8;

        ctx.beginPath();
        ctx.moveTo(p.trail[0][0], p.trail[0][1]);
        for (let i = 1; i < p.trail.length; i++) {
            ctx.lineTo(p.trail[i][0], p.trail[i][1]);
        }

        ctx.lineTo(p.x, p.y);
        ctx.stroke();
        ctx.restore();
    }

    drawHead(p) {
        const { ctx } = this;

        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.angle);

        ctx.fillStyle = p.color;
        ctx.shadowBlur = 10;
        ctx.shadowColor = "rgba(0,0,0,0.3)";
        ctx.fillRect(-15, -15, 30, 30);

        ctx.strokeStyle = "white";
        ctx.lineWidth = 2;
        ctx.strokeRect(-15, -15, 30, 30);
        ctx.restore();

        this.drawNameplate(p.name, p.x, p.y - 35);
    }

    drawNameplate(name, x, y) {
        const { ctx } = this;
        ctx.font = "bold 14px 'Segoe UI', Arial";
        const textWidth = ctx.measureText(name).width;
        const padding = 8;

        ctx.fillStyle = "rgba(0, 0, 0, 0.5)";
        ctx.beginPath();
        const rectX = x - textWidth / 2 - padding;
        const rectY = y - 12;
        const rectW = textWidth + padding * 2;
        const rectH = 20;
        ctx.roundRect(rectX, rectY, rectW, rectH, 10);
        ctx.fill();

        ctx.fillStyle = "white";
        ctx.textAlign = "center";
        ctx.fillText(name, x, y + 3);
    }

    drawGrid(camera) {
        const { ctx, canvas } = this;
        ctx.save();

        ctx.fillStyle = "#fafafa";
        ctx.fillRect(0, 0, this.MAP_SIZE, this.MAP_SIZE);

        ctx.strokeStyle = "rgba(0, 0, 0, 0.05)";
        ctx.lineWidth = 1;

        const startX = Math.floor(camera.x / this.GRID_SIZE) * this.GRID_SIZE;
        const startY = Math.floor(camera.y / this.GRID_SIZE) * this.GRID_SIZE;
        const endX = startX + canvas.width + this.GRID_SIZE;
        const endY = startY + canvas.height + this.GRID_SIZE;

        ctx.beginPath();
        for (let x = startX; x <= endX; x += this.GRID_SIZE) {
            if (x < 0 || x > this.MAP_SIZE) continue;
            ctx.moveTo(x, Math.max(0, startY));
            ctx.lineTo(x, Math.min(this.MAP_SIZE, endY));
        }
        for (let y = startY; y <= endY; y += this.GRID_SIZE) {
            if (y < 0 || y > this.MAP_SIZE) continue;
            ctx.moveTo(Math.max(0, startX), y);
            ctx.lineTo(Math.min(this.MAP_SIZE, endX), y);
        }
        ctx.stroke();
        ctx.restore();
    }

    updateUI(gameState, myId) {
        if (!this.ui.hud) return;

        this.ui.hud.style.display = 'block';
        this.ui.lb.style.display = 'block';

        this.ui.playerCount.innerText = `Players: ${gameState.players.length}`;

        const me = gameState.players.find(p => p.id === myId);
        if (me) {
            this.ui.currentScore.innerText = `Score: ${Math.round(me.score)}`;
        }

        if (gameState.leaderboard) {
            this.ui.leaderboardList.innerHTML = gameState.leaderboard.map((entry, i) => `
                <div class="leaderboard-entry" style="color: ${entry.color}">
                    <span>${i + 1}. <span class="leaderboard-name">${entry.name}</span></span>
                    <span>${Math.round(entry.score)}</span>
                </div>
            `).join('');
        }
    }
}

class Game {
    constructor() {
        this.canvas = document.getElementById('game-canvas');
        this.ctx = this.canvas.getContext('2d');
        this.renderer = new Renderer(this.ctx, this.canvas);
        this.camera = new Camera();
        this.ws = null;
        this.isPlaying = false;
        this.gameState = { players: [] };
        this.myNickname = "";
        this.mouseX = 0;
        this.mouseY = 0;
        this.myId = null;

        this.initEventListeners();
    }

    initEventListeners() {
        window.addEventListener('resize', () => this.resize());
        this.resize();

        document.getElementById('play-button').addEventListener('click', () => this.start());
        document.getElementById('restart-button').addEventListener('click', () => {
            document.getElementById('game-over-screen').style.display = 'none';
            document.getElementById('login-screen').style.display = 'flex';
        });

        window.addEventListener('mousemove', (e) => {
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
        });
    }

    resize() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    }

    start() {
        const nick = document.getElementById('nickname-input').value.trim();
        if (!nick) return alert("Please enter a nickname");

        this.myNickname = nick;
        this.connect();
    }

    connect() {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.ws = new WebSocket(`${protocol}//${location.host}/game?name=${encodeURIComponent(this.myNickname)}`);

        this.ws.onopen = () => {
            this.isPlaying = true;
            document.getElementById('login-screen').style.display = 'none';
            this.startInputLoop();
            this.render();
        };

        this.ws.onmessage = (e) => {
            const data = JSON.parse(e.data);

            if (data.type === "INIT") {
                this.myId = data.playerId;
                console.log("My assigned ID:", this.myId);
                return;
            }

            this.gameState = data;
        };

        this.ws.onclose = (e) => {
            this.isPlaying = false;
            if (e.code === 4000) this.showGameOver();
            else location.reload();
        };
    }

    startInputLoop() {
        setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({
                    x: this.mouseX + this.camera.x,
                    y: this.mouseY + this.camera.y
                }));
            }
        }, 50);
    }

    showGameOver() {
        const me = this.gameState.players.find(p => p.name === this.myId);
        document.getElementById('final-score').innerText = me ? Math.round(me.score) : 0;
        document.getElementById('game-over-screen').style.display = 'flex';
    }

    render() {
        if (!this.isPlaying) return;

        const me = this.gameState.players.find(p => p.id === this.myId);
        this.camera.update(me, this.canvas);
        this.renderer.draw(this.gameState, this.camera, this.myId);

        requestAnimationFrame(() => this.render());
    }
}

new Game();