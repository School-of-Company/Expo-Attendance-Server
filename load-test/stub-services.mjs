// 부하 테스트용 다른 서비스 흉내(유저·박람회·신청). 실제 서비스 없이 참여 서비스의 입구 스캔 경로만 돌린다.
// 사용: node load-test/stub-services.mjs   (환경 변수는 load-test/README.md 참고)
import { createServer } from 'node:http';

const port = Number(process.env.STUB_PORT ?? 18090);
const latencyMs = Number(process.env.STUB_LATENCY_MS ?? 20);
const jitterMs = Number(process.env.STUB_JITTER_MS ?? 10);
// 이 비율만큼 5xx로 답한다(0~1). 다른 서비스 장애 때 참여 서비스가 어떻게 버티는지 볼 때 쓴다
const failRate = Number(process.env.STUB_FAIL_RATE ?? 0);
// 신청 서비스만 더 느리게 만든다(취소 보호 락 안에서 호출되는 경로)
const applicationExtraMs = Number(process.env.STUB_APPLICATION_EXTRA_MS ?? 0);

const entered = new Set();

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const delay = (extra = 0) => sleep(latencyMs + Math.random() * jitterMs + extra);
const day = (offset) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);

function send(res, status, body) {
  if (body === undefined) {
    res.writeHead(status).end();
    return;
  }
  res.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify(body));
}

async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  try {
    return JSON.parse(Buffer.concat(chunks).toString() || '{}');
  } catch {
    return {};
  }
}

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://stub');
  const path = url.pathname;

  if (path === '/__reset') {
    entered.clear();
    return send(res, 204);
  }

  const isApplication = path.startsWith('/internal/program-applications/');
  await delay(isApplication ? applicationExtraMs : 0);
  if (failRate > 0 && Math.random() < failRate) return send(res, 503, { message: 'stub failure' });

  if (req.method === 'POST' && path === '/internal/entries') {
    const body = await readJson(req);
    const key = `${body.expoId}:${body.participantId ?? body.phoneNumber}`;
    if (entered.has(key)) return send(res, 409, { message: 'already entered' });
    entered.add(key);
    return send(res, 200, {
      id: body.participantId ?? 1,
      name: '부하테스트',
      phoneNumber: '010-0000-0000',
      personalInformationStatus: true,
      participationType: 'STANDARD',
      occupation: 'STUDENT',
      school: null,
    });
  }
  if (req.method === 'POST' && path === '/internal/standard-participants/verify') return send(res, 204);
  if (req.method === 'POST' && path === '/internal/standard-participants/cancellation-status') {
    const body = await readJson(req);
    return send(res, 200, {
      participantId: body.participantId,
      representative: true,
      entered: false,
      traineeLinked: false,
      linkedTraineeId: null,
    });
  }
  if (req.method === 'GET' && /^\/internal\/expo\/[^/]+$/.test(path)) {
    return send(res, 200, { startedDay: day(-1), finishedDay: day(1) });
  }
  if (req.method === 'GET' && /^\/internal\/expo\/[^/]+\/standard-programs\/\d+$/.test(path)) {
    return send(res, 200, { id: Number(path.split('/').pop()), title: '부하테스트 프로그램' });
  }
  if (req.method === 'GET' && isApplication) return send(res, 200, { applied: true });

  return send(res, 404, { message: 'stub: unknown path' });
}).listen(port, '127.0.0.1', () => {
  console.log(`stub services on 127.0.0.1:${port} latency=${latencyMs}+${jitterMs}ms failRate=${failRate} applicationExtra=${applicationExtraMs}ms`);
});
