import { useEffect, useState } from 'react';
import { localToday } from '../utils/contractAdministration';

export default function useCalendarDate() {
    const [today, setToday] = useState(localToday);
    useEffect(() => {
        const refresh = () => setToday(localToday());
        const interval = setInterval(refresh, 60000);
        window.addEventListener('focus', refresh);
        document.addEventListener('visibilitychange', refresh);
        return () => {
            clearInterval(interval);
            window.removeEventListener('focus', refresh);
            document.removeEventListener('visibilitychange', refresh);
        };
    }, []);
    return today;
}
