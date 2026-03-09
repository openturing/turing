import { TurSNSiteMetricsService } from "@/services/sn/sn.site.metrics.service";
import { useEffect, useState } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, XAxis, YAxis } from 'recharts';

const turSNSiteMetricsService = new TurSNSiteMetricsService(); // Instância do serviço para buscar métricas de acesso em tempo real

export default function RealTimeDashboard() {
    const [data, setData] = useState<any[]>([]);

    const refresh = async () => {
        try {
            // Chama o serviço do Turing ES
            const res = await turSNSiteMetricsService.live('2727a7d5-7450-4a4d-b3dc-01e729103ce4');
            if (Array.isArray(res)) {
                setData(res);
            }
        } catch (error) {
            console.error("Erro na busca de métricas", error);
        }
    };

    useEffect(() => {
        refresh();
        const t = setInterval(refresh, 1000); // Sincronizado com o balde de 1s do Java
        return () => clearInterval(t);
    }, []);

    return (
        <div className="h-[300px] w-full bg-slate-950 p-4 rounded-lg border border-slate-800">
            <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data}>
                    <defs>
                        <linearGradient id="colorAcc" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8} />
                            <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                        </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#1e293b" />
                    <XAxis
                        dataKey="time"
                        type="number"
                        domain={['dataMin', 'dataMax']} // Mantém o eixo fixo no tempo do backend
                        tickFormatter={(unix) => new Date(unix * 1000).toLocaleTimeString([], { second: '2-digit' })}
                        stroke="#475569"
                        fontSize={10}
                    />
                    <YAxis stroke="#475569" fontSize={10} domain={[0, 'auto']} />
                    <Area
                        type="step" // 'step' é excelente para mostrar baldes de tempo sem "minhocas"
                        dataKey="accesses"
                        stroke="#3b82f6"
                        fillOpacity={1}
                        fill="url(#colorAcc)"
                        isAnimationActive={false} // CRUCIAL para estabilidade visual
                    />
                </AreaChart>
            </ResponsiveContainer>
        </div>
    );
}