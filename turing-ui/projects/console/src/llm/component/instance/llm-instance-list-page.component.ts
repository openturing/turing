import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurLLMInstance } from '../../model/llm-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurLLMInstanceService } from '../../service/llm-instance.service';
import { Router, RouterModule } from '@angular/router';

@Component({
    selector: 'llm-instance-list-page',
    templateUrl: './llm-instance-list-page.component.html',
    standalone: false
})
export class TurLLMInstanceListPageComponent implements OnInit {
  private turLLMInstances: Observable<TurLLMInstance[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turLLMInstanceService: TurLLMInstanceService,
              private router: Router) {
    this.turLLMInstances = turLLMInstanceService.query();
    this.filterText = "";
  }

  getTurLLMInstances(): Observable<TurLLMInstance[]> {

    return this.turLLMInstances;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
