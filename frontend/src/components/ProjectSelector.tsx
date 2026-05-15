import { useEffect, useState } from 'react';
import { Select } from 'antd';
import { listProjects } from '../api/client';
import type { ProjectResponse } from '../types';

interface ProjectSelectorProps {
  value?: number;
  onChange: (projectId: number | undefined) => void;
  allowAll?: boolean;
  placeholder?: string;
  style?: React.CSSProperties;
}

export default function ProjectSelector({
  value,
  onChange,
  allowAll = false,
  placeholder = 'Select project',
  style,
}: ProjectSelectorProps) {
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const data = await listProjects();
        setProjects(data);
        if (!allowAll && data.length > 0 && value === undefined) {
          onChange(data[0].id);
        }
      } catch (err) {
        console.error('Failed to fetch projects:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const options = [
    ...(allowAll
      ? [{ label: 'All Projects', value: -1 }]
      : []),
    ...projects.map((p) => ({
      label: `${p.name} (${p.academicYear} ${p.semester})`,
      value: p.id,
    })),
  ];

  return (
    <Select
      value={value ?? (allowAll ? -1 : undefined)}
      onChange={(v) => onChange(v === -1 ? undefined : v)}
      options={options}
      loading={loading}
      placeholder={placeholder}
      style={{ minWidth: 250, ...style }}
    />
  );
}
